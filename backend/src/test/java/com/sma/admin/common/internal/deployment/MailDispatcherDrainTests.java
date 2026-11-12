package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.context.request.RequestContextHolder;
import com.sma.admin.common.deployment.DeploymentWork;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.*;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest
@Import(MailDispatcherDrainTests.Policy.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class MailDispatcherDrainTests {
    @TestConfiguration static class Policy {
        @Bean MailSendPolicy drainMailPolicy(){return new MailSendPolicy(){
            public String application(){return "drain";}
            public boolean canSend(ActorRef actor,MailSendApi.Command command){return actor.kind()==ActorRef.Kind.ADMIN_USER&&command.recipient().endsWith("@example.test");}
        };}
    }
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-mail-drain-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));
        registry.add("sma.common.credentials.key-file",()->keyFile.toString());
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired DeploymentWorkCoordinator admission;
    @Autowired MailSendApi api;
    @Autowired MailStatusQuery status;
    @Autowired MailProfileService profiles;
    @Autowired MailTemplateService templates;
    @Autowired MailDispatchCycle cycle;
    @MockitoSpyBean SmtpMailSender sender;
    @MockitoSpyBean TenantEntryQuery tenants;

    @Test void drainPreservesCommittedAcceptedAndUnknownResultsAndUnclaimedRowsUntilTransportReallyEnds()throws Exception{
        long acceptedTenant=97240,unknownTenant=97241;
        doAnswer(i->Optional.of(new TenantEntryQuery.SecurityState(i.getArgument(0),1,true,true))).when(tenants).securityState(anyLong());
        var accepted=fixture(acceptedTenant);var unknown=fixture(unknownTenant);
        var firstEntered=new CountDownLatch(1);var secondEntered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var transportBranches=new ConcurrentLinkedQueue<DeploymentWork.Permit>();
        doAnswer(i->{
            var prepared=i.getArgument(0,MailWorkService.Prepared.class);
            transportBranches.add(i.getArgument(1));
            (prepared.claim().tenantId()==acceptedTenant?firstEntered:secondEntered).countDown();
            if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Mail transport not released");
            return prepared.claim().tenantId()==acceptedTenant?MailWorkService.Outcome.ACCEPTED:MailWorkService.Outcome.UNKNOWN;
        }).when(sender).send(any(),any());
        var workers=Executors.newFixedThreadPool(2,Thread.ofPlatform().inheritInheritableThreadLocals(false).factory());
        try{
            // SKIP LOCKED may legitimately yield no claim during a concurrent first scan.
            // Establish both admitted sends deterministically, without changing production retries or locks.
            var first=workers.submit(()->cycle.runTenant(acceptedTenant));awaitTransport(first,firstEntered);
            var second=workers.submit(()->cycle.runTenant(unknownTenant));awaitTransport(second,secondEntered);
            assertThat(admission.snapshot().activeWork()).isEqualTo(2);
            assertThat(host(admission::beginDrain)).isTrue();
            assertState(acceptedTenant,accepted.getFirst(),"SENDING");assertState(unknownTenant,unknown.getFirst(),"SENDING");
            release.countDown();assertThat(first.get(10,TimeUnit.SECONDS)).isOne();assertThat(second.get(10,TimeUnit.SECONDS)).isOne();
            assertState(acceptedTenant,accepted.getFirst(),"ACCEPTED");assertState(unknownTenant,unknown.getFirst(),"UNKNOWN");
            assertState(acceptedTenant,accepted.getLast(),"QUEUED");assertState(unknownTenant,unknown.getLast(),"QUEUED");
            assertThat(admission.snapshot().activeWork()).isEqualTo(2); // DB committed; transport branches still executing
            transportBranches.forEach(DeploymentWork.Permit::close);assertThat(admission.snapshot().activeWork()).isZero();
            assertThat(host(()->cycle.runTenant(acceptedTenant))).isZero();assertThat(host(()->cycle.runTenant(unknownTenant))).isZero();
            verify(sender,times(2)).send(any(),any());assertState(unknownTenant,unknown.getFirst(),"UNKNOWN");
            assertState(acceptedTenant,accepted.getLast(),"QUEUED");assertState(unknownTenant,unknown.getLast(),"QUEUED");
        }finally{
            release.countDown();workers.shutdownNow();workers.awaitTermination(10,TimeUnit.SECONDS);
            transportBranches.forEach(DeploymentWork.Permit::close);
        }
    }
    private void awaitTransport(Future<Integer> worker,CountDownLatch entered)throws Exception{
        if(entered.await(10,TimeUnit.SECONDS))return;
        if(worker.isDone())fail("Mail worker returned before transport: "+worker.get());
        fail("Mail worker must reach transport before drain");
    }
    private List<Long> fixture(long tenant){return admin(tenant,()->{
        var profile=profiles.create(new MailProfileService.Input("drain","SMTP","smtp.example.test",587,MailProfileService.TlsMode.STARTTLS,
                "mailer@example.test","test-password".toCharArray(),"from@example.test","Sender",Set.of("drain"),100,10,true));
        var content=new MailTemplateContent("Drain","Test {{code}}",false,Map.of("code",new MailTemplateContent.Variable(MailTemplateContent.Type.TEXT,10,true)),Set.of());
        var draft=templates.create(new MailTemplateService.Input("drain","drain","notice","en-US",profile.code(),content));
        var template=templates.publish(draft.id(),draft.version());var ids=new ArrayList<Long>();
        for(int i=0;i<2;i++)ids.add(api.enqueue(new MailSendApi.Command("drain","notice",template.code(),"en-US","to@example.test",
                Map.of("code","123456"),Instant.now().plusSeconds(300),"drain-"+i)).id());
        return ids;
    });}
    private void assertState(long tenant,long id,String expected){assertThat(admin(tenant,()->status.get(id).state())).isEqualTo(expected);}
    private <T>T admin(long tenant,Supplier<T> operation){return Users.as(new SmaPrincipal(71,"mail-drain",tenant,"admin",null,false,DataAccess.none(),
            Set.of("mail:profile:view","mail:profile:manage","mail:template:view","mail:template:manage","mail:template:publish"),"UTC"),operation);}
    private static <T>T host(Supplier<T> operation){
        var request=RequestContextHolder.getRequestAttributes();var security=SecurityContextHolder.getContext();
        RequestContextHolder.resetRequestAttributes();SecurityContextHolder.clearContext();
        try{return operation.get();}finally{RequestContextHolder.setRequestAttributes(request);SecurityContextHolder.setContext(security);}
    }
}
