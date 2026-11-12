package com.sma.admin.extension.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.admin.common.BizException;
import com.sma.admin.common.extension.ExtensionJobPolicy;
import com.sma.admin.common.security.RuntimePermissionRegistrar;
import com.sma.admin.extension.ExtensionWork;
import com.sma.admin.support.*;
import com.sma.extension.api.*;
import com.sma.extension.protocol.*;
import com.sma.extension.runtime.PluginRuntime;

class ExtensionDeploymentTests {
    @TempDir Path directory;
    final DeploymentWorkFixture admission=new DeploymentWorkFixture();
    final NodeControlClient control=mock(NodeControlClient.class);
    final ExtensionWorkJournal journal=mock(ExtensionWorkJournal.class);
    final RuntimePermissionRegistrar permissions=mock(RuntimePermissionRegistrar.class);
    final ExtensionJobPolicy jobs=mock(ExtensionJobPolicy.class);
    ExtensionRuntimeService service()throws Exception{
        var trust=directory.resolve("trust.json");
        var key=java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.write(trust,ProtocolJson.write(List.of(new ExtensionRuntimeService.PublicKeySpec("test","production","Ed25519",
                Base64.getEncoder().encodeToString(key.getPublic().getEncoded()),java.time.Instant.now().minusSeconds(60),java.time.Instant.now().plusSeconds(3600),Set.of(SignedClaims.Purpose.LEASE),false))));
        var properties=new ExtensionProperties(true,URI.create("https://release.example"),"node","x".repeat(32),"production","security","host",trust,directory,List.of("deployment"),null);
        when(jobs.id()).thenReturn("scheduled");when(jobs.permissions()).thenReturn(Set.of("extension:invoke:example"));
        return new ExtensionRuntimeService(properties,control,mock(HostCheckpoint.class),journal,mock(RuntimeAdmission.class),permissions,List.of(),List.of(jobs),new ExtensionTelemetry(),admission);
    }
    @Test void refusedSyncDoesNotReadControlAndRecoveryStartsNewCycle()throws Exception{
        var service=service();
        try{
            admission.accepting=false;service.synchronize();assertThat(service.status().lastSync()).isEqualTo("DEFERRED");verifyNoInteractions(control,journal,permissions);
            admission.accepting=true;doThrow(new java.io.IOException("test-unavailable")).when(control).acknowledgeKeys(anyMap());
            service.synchronize();verify(control).acknowledgeKeys(anyMap());assertThat(service.status().lastSync()).isEqualTo("CONTROL_UNAVAILABLE");assertThat(admission.active).hasValue(0);
            service.close();service.synchronize();verifyNoMoreInteractions(control);
            assertThatThrownBy(()->service.invokeJob("scheduled",1,"object")).isInstanceOf(BizException.class);
        }finally{service.close();}
    }
    @Test void inFlightControlIoRetainsPermitThroughDrainAndFailure()throws Exception{
        var service=service();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(i->{assertThat(admission.active).hasValue(1);entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Control not released");throw new java.io.IOException("test");}).when(control).acknowledgeKeys(anyMap());
        try(var runner=Executors.newSingleThreadExecutor()){
            var result=runner.submit(service::synchronize);
            try{assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();admission.accepting=false;assertThat(admission.active).hasValue(1);}
            finally{release.countDown();}
            result.get(3,TimeUnit.SECONDS);assertThat(admission.active).hasValue(0);service.synchronize();verify(control,times(1)).acknowledgeKeys(anyMap());
        }finally{release.countDown();service.close();}
    }
    @Test void strictHostRejectsHttpJobAndOuterTransactionBeforeControlOrPolicy()throws Exception{
        var service=service();
        try{
            MaintenanceCallerAssertions.hostOnly(service::synchronize,()->service.invokeJob("scheduled",1,"object"));
            verifyNoInteractions(control,journal,permissions);verify(jobs,never()).resolve(anyString());
            admission.accepting=false;assertThatThrownBy(()->service.invokeJob("scheduled",1,"object")).isInstanceOf(BizException.class);
            verify(jobs,never()).resolve(anyString());
        }finally{service.close();}
    }
    @Test void realSchedulerDoesNotInheritRequestContextAndCanBeShutdown()throws Exception{
        var service=mock(ExtensionRuntimeService.class);var seen=new CountDownLatch(1);var error=new AtomicReference<Throwable>();
        var inherited=new InheritableThreadLocal<String>();inherited.set("caller");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()),true);
        doAnswer(i->{try{assertThat(inherited.get()).isNull();assertThat(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).isNull();}
            catch(Throwable failure){error.set(failure);}finally{seen.countDown();}return null;}).when(service).synchronize();
        var scheduler=new ExtensionConfiguration().extensionControlScheduler(service);
        try{assertThat(seen.await(3,TimeUnit.SECONDS)).isTrue();assertThat(error.get()).isNull();}
        finally{scheduler.shutdown();inherited.remove();org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    }
    @Test void admittedJobCompletesOriginalClaimWhileNewJobIsDenied()throws Exception{
        var service=spy(service());var runtime=mock(PluginRuntime.class);doReturn(runtime).when(service).runtime();
        byte[] input="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);var selection=new PluginRuntime.Selection("deployment",1,"bundle");
        var saved=new ExtensionWorkJournal.Saved(41,"deployment","example","execute",Hashes.sha256(input),selection,List.of(),"RECEIVED",null,null,null);
        var claimed=new ExtensionWorkJournal.Saved(41,"deployment","example","execute",saved.inputHash(),selection,List.of(),"RUNNING","claim",null,null);
        when(jobs.resolve("object")).thenAnswer(i->{assertThat(admission.active).hasValue(1);assertThat(WorkCaller.current().tenantId()).isEqualTo(51);return new ExtensionJobPolicy.Job("deployment","example","execute","stable",input);});
        when(journal.find(anyString(),anyString(),anyString(),anyString())).thenReturn(saved);when(journal.claim(41)).thenReturn(claimed);
        when(journal.get(41)).thenReturn(claimed);when(journal.view(claimed)).thenReturn(new ExtensionWork(41,"SUCCEEDED",1,"bundle",input,null));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(runtime.invoke(any(),anyString(),anyString(),any(),anyString(),any(),any())).thenAnswer(i->{
            assertThat(admission.active).hasValue(1);entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Execution not released");return new PluginResult(input);
        });
        doAnswer(i->{assertThat(admission.active).hasValue(1);return null;}).when(journal).succeeded(eq(41L),eq("claim"),any());
        try(var runner=Executors.newSingleThreadExecutor()){
            var result=runner.submit(()->service.invokeJob("scheduled",51,"object"));
            try{assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();admission.accepting=false;
                assertThatThrownBy(()->service.invokeJob("scheduled",51,"other")).isInstanceOf(BizException.class);assertThat(admission.active).hasValue(1);
            }finally{release.countDown();}
            assertThat(result.get(3,TimeUnit.SECONDS).state()).isEqualTo("SUCCEEDED");assertThat(admission.active).hasValue(0);
            verify(journal).succeeded(eq(41L),eq("claim"),any());verify(journal,never()).unknown(anyLong(),anyString(),anyString());verify(jobs,never()).resolve("other");
        }finally{release.countDown();service.close();}
    }
    @Test void failedResultCommitRetainsUnknownAndNeverInvokesTwice()throws Exception{
        var service=spy(service());var runtime=mock(PluginRuntime.class);doReturn(runtime).when(service).runtime();
        byte[] input="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);var selection=new PluginRuntime.Selection("deployment",1,"bundle");
        var saved=new ExtensionWorkJournal.Saved(42,"deployment","example","execute",Hashes.sha256(input),selection,List.of(),"RECEIVED",null,null,null);
        var claimed=new ExtensionWorkJournal.Saved(42,"deployment","example","execute",saved.inputHash(),selection,List.of(),"RUNNING","claim",null,null);
        when(jobs.resolve(anyString())).thenReturn(new ExtensionJobPolicy.Job("deployment","example","execute","stable",input));
        when(journal.find(anyString(),anyString(),anyString(),anyString())).thenReturn(saved);when(journal.claim(42)).thenReturn(claimed);when(journal.get(42)).thenReturn(claimed);
        when(runtime.invoke(any(),anyString(),anyString(),any(),anyString(),any(),any())).thenReturn(new PluginResult(input));
        doThrow(new IllegalStateException("commit unavailable")).when(journal).succeeded(eq(42L),eq("claim"),any());
        doAnswer(i->{assertThat(admission.active).hasValue(1);return null;}).when(journal).unknown(eq(42L),eq("claim"),anyString());
        service.invokeJob("scheduled",51,"object");verify(journal).unknown(42,"claim","execution-outcome-unknown");
        verify(runtime,times(1)).invoke(any(),anyString(),anyString(),any(),anyString(),any(),any());assertThat(admission.active).hasValue(0);service.close();
    }
}
