package com.sma.admin.extension;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.security.*;
import com.sma.admin.extension.internal.service.ExtensionWorkJournal;
import com.sma.admin.support.IntegrationTest;
import com.sma.admin.support.Users;
import com.sma.extension.protocol.*;
import com.sma.extension.protocol.ExecutionDocuments.Member;
import com.sma.extension.runtime.PluginRuntime;

@IntegrationTest
class ExtensionWorkJournalTests {
    @Autowired ExtensionWorkJournal journal;
    private static final String PERMISSION="extension:invoke:example";
    @Test void reconciliationConfirmsOriginalResultOrFencesCancellationWithoutRetryAndReleasesReferencesOnce() {
        var actor=user(911,911,Set.of(PERMISSION));
        Users.as(actor,() -> {
            var accepted=journal.accept("reconcile-one","example","execute",PERMISSION,UUID.randomUUID().toString(),"a".repeat(64),new PluginRuntime.Selection("reconcile-one",1,"original"),List.of(new Member("example","artifact","7".repeat(64))));
            var claimed=journal.claim(accepted.id());
            var pending=new com.sma.admin.common.extension.ExtensionWorkReconciler.Resolution(com.sma.admin.common.extension.ExtensionWorkReconciler.Outcome.PENDING,null,null);
            assertThat(journal.reconcile(accepted.id(),claimed.claimId(),pending).state()).isEqualTo("RUNNING");assertThat(journal.references("7".repeat(64))).isEqualTo(1);
            var success=new com.sma.admin.common.extension.ExtensionWorkReconciler.Resolution(com.sma.admin.common.extension.ExtensionWorkReconciler.Outcome.SUCCEEDED,"{\"confirmed\":true}".getBytes(StandardCharsets.UTF_8),"b".repeat(64));
            assertThatThrownBy(() -> journal.reconcile(accepted.id(),"wrong-claim",success)).hasMessage("work-claim-conflict");
            journal.unknown(accepted.id(),claimed.claimId(),"timeout");
            assertThat(journal.reconcile(accepted.id(),claimed.claimId(),success).state()).isEqualTo("SUCCEEDED");
            assertThat(journal.reconcile(accepted.id(),claimed.claimId(),success).result()).isEqualTo(success.result());assertThat(journal.references("7".repeat(64))).isZero();assertThat(journal.claim(accepted.id()).claimId()).isNull();
            var next=journal.accept("reconcile-two","example","execute",PERMISSION,UUID.randomUUID().toString(),"a".repeat(64),new PluginRuntime.Selection("reconcile-two",1,"original"),List.of(new Member("example","artifact","8".repeat(64))));
            var running=journal.claim(next.id());var cancelled=new com.sma.admin.common.extension.ExtensionWorkReconciler.Resolution(com.sma.admin.common.extension.ExtensionWorkReconciler.Outcome.CANCELLED,null,"c".repeat(64));
            assertThat(journal.reconcile(next.id(),running.claimId(),cancelled).state()).isEqualTo("CANCELLED");assertThat(journal.references("8".repeat(64))).isZero();
            assertThatThrownBy(() -> journal.succeeded(next.id(),running.claimId(),"{}".getBytes(StandardCharsets.UTF_8))).hasMessage("work-claim-conflict");return null;
        });
    }
    @Test void retryKeepsOriginalVersionAndSuccessfulResultReleasesOnlyItsReferences() {
        var user=user(901,901,Set.of(PERMISSION));String hash="1".repeat(64),key=UUID.randomUUID().toString();
        Users.as(user,() -> {
            var selection=new PluginRuntime.Selection("journal-one",1,"old-vector");
            var members=List.of(new Member("example","artifact-one",hash));
            var accepted=journal.accept("journal-one","example","execute",PERMISSION,key,"a".repeat(64),selection,members);
            var repeated=journal.accept("journal-one","example","execute",PERMISSION,key,"a".repeat(64),new PluginRuntime.Selection("journal-one",2,"new-vector"),List.of(new Member("example","artifact-two","2".repeat(64))));
            assertThat(repeated.id()).isEqualTo(accepted.id());assertThat(repeated.selection()).isEqualTo(selection);
            assertThat(journal.references(hash)).isEqualTo(1);
            assertThat(journal.retained("journal-one").bundleIds()).containsExactly("old-vector");
            assertThatThrownBy(() -> journal.accept("journal-one","example","execute",PERMISSION,key,"b".repeat(64),selection,members)).hasMessage("work-input-conflict");
            var claimed=journal.claim(accepted.id());assertThat(claimed.claimId()).isNotBlank();
            assertThat(journal.claim(accepted.id()).claimId()).isNull();
            byte[] result="{\"saved\":true}".getBytes(StandardCharsets.UTF_8);journal.succeeded(accepted.id(),claimed.claimId(),result);
            assertThat(journal.get(accepted.id()).result()).isEqualTo(result);assertThat(journal.references(hash)).isZero();
            assertThat(journal.claim(accepted.id()).claimId()).isNull();return null;
        });
    }
    @Test void concurrentWorkersClaimOnceAndUnknownOutcomeRetainsVersion() throws Exception {
        var user=user(902,902,Set.of(PERMISSION));String key=UUID.randomUUID().toString(),hash="3".repeat(64);
        var accepted=Users.as(user,() -> journal.accept("journal-two","example","execute",PERMISSION,key,"a".repeat(64),new PluginRuntime.Selection("journal-two",1,"vector"),List.of(new Member("example","artifact",hash))));
        List<Callable<ExtensionWorkJournal.Saved>> calls=java.util.stream.IntStream.range(0,8).mapToObj(i -> (Callable<ExtensionWorkJournal.Saved>)() -> Users.as(user,() -> journal.claim(accepted.id()))).toList();
        try(var threads=Executors.newFixedThreadPool(8)) {
            var results=threads.invokeAll(calls);var claimed=new java.util.ArrayList<ExtensionWorkJournal.Saved>();
            for(var result:results) {var value=result.get();if(value.claimId()!=null)claimed.add(value);}
            assertThat(claimed).hasSize(1);
            Users.as(user,() -> {journal.unknown(accepted.id(),claimed.getFirst().claimId(),"external-result-unknown");
                assertThat(journal.get(accepted.id()).state()).isEqualTo("UNKNOWN");assertThat(journal.claim(accepted.id()).claimId()).isNull();
                assertThat(journal.references(hash)).isEqualTo(1);return null;});
        }
    }
    @Test void workLookupRequiresTenantOwnerAndCurrentEntryPermission() {
        var owner=user(903,903,Set.of(PERMISSION));String key=UUID.randomUUID().toString();
        var saved=Users.as(owner,() -> journal.accept("journal-three","example","execute",PERMISSION,key,"a".repeat(64),new PluginRuntime.Selection("journal-three",1,"vector"),List.of(new Member("example","artifact","4".repeat(64)))));
        Users.as(user(903,904,Set.of(PERMISSION)),() -> {assertThatThrownBy(() -> journal.get(saved.id())).hasMessage("work-not-found");return null;});
        Users.as(user(904,903,Set.of(PERMISSION)),() -> {assertThatThrownBy(() -> journal.get(saved.id())).isInstanceOf(AccessDeniedException.class);return null;});
        Users.as(user(903,903,Set.of()),() -> {assertThatThrownBy(() -> journal.get(saved.id())).isInstanceOf(AccessDeniedException.class);return null;});
    }
    private SmaPrincipal user(long id,long tenant,Set<String> permissions){return new SmaPrincipal(id,"worker-"+id,tenant,"admin",null,false,DataAccess.unrestricted(),permissions,"UTC");}
}
