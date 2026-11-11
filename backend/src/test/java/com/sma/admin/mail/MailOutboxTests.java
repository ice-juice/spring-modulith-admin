package com.sma.admin.mail;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.internal.persistence.*;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest @Import(MailOutboxTests.Policy.class)
class MailOutboxTests {
    @TestConfiguration static class Policy {
        @Bean MailReceiptPolicy receiptsPolicy(){return (actor,c)->actor.subject().equals("app:receipt-gateway")
                && c.application().equals("shop") && c.provider().equals("test-smtp");}
        @Bean MailSendPolicy policy() {return new MailSendPolicy(){
            public String application(){return "shop";}
            public boolean canSend(ActorRef actor,MailSendApi.Command c){return actor.kind()==ActorRef.Kind.ADMIN_USER && c.recipient().endsWith("@example.test");}
        };}
    }
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);keyFile=Files.createTempFile("sma-mail-outbox-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(key));
        registry.add("sma.common.credentials.key-file",()->keyFile.toString());
        registry.add("sma.mail.work.maximum-queued-per-profile",()->2);
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired MailSendApi api; @Autowired MailStatusQuery status;
    @Autowired MailReceiptApi receipts; @Autowired MailSuppressionMapper suppressionRows;
    @Autowired MailProfileService profiles; @Autowired MailTemplateService templates;
    @Autowired MailDeliveryMapper rows; @Autowired MailAttemptMapper attempts; @Autowired MailWorkService work;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean MailWorkClock clock;
    @MockitoSpyBean SmtpMailSender sender;
    @Autowired MailDispatchCycle dispatch;
    @MockitoSpyBean TenantEntryQuery tenants;
    private final Instant now=Instant.parse("2026-11-11T00:00:00Z");
    @BeforeEach void time(){org.mockito.Mockito.doReturn(now).when(clock).now();
        org.mockito.Mockito.doAnswer(i->Optional.of(new TenantEntryQuery.SecurityState(i.getArgument(0),1,true,true))).when(tenants).securityState(org.mockito.ArgumentMatchers.anyLong());}
    private <T>T as(long tenant,Supplier<T> action){return Users.as(principal(tenant,71),action);}
    private SmaPrincipal principal(long tenant,long id){return new SmaPrincipal(id,"mail-test",tenant,"admin",null,false,DataAccess.none(),
            Set.of("mail:profile:view","mail:profile:manage","mail:template:view","mail:template:manage","mail:template:publish"),"UTC");}
    private <T>T job(long tenant,Supplier<T> action){return ActorContext.withinJob(tenant,"mail-dispatch",action);}
    private <T>T gateway(long tenant,Supplier<T> action){return Users.as(SmaPrincipal.forApplication(tenant,"admin","receipt-gateway"),action);}
    private record Fixture(long tenant,MailProfileService.View profile,MailTemplateService.View template){}
    private Fixture fixture(long tenant,int minute){return as(tenant,()->{
        String suffix=UUID.randomUUID().toString().substring(0,8);
        var p=profiles.create(new MailProfileService.Input("p-"+suffix,"SMTP","smtp.example.test",587,MailProfileService.TlsMode.STARTTLS,
                "mailer@example.test","password".toCharArray(),"sender@example.test","Sender",Set.of("shop"),100,minute,true));
        var content=new MailTemplateContent("Notice","Code {{code}}",false,Map.of("code",new MailTemplateContent.Variable(MailTemplateContent.Type.TEXT,20,true)),Set.of());
        var t=templates.create(new MailTemplateService.Input("t-"+suffix,"shop","registration","zh-CN",p.code(),content));
        return new Fixture(tenant,p,templates.publish(t.id(),t.version()));
    });}
    private MailSendApi.Command command(Fixture f,String key){return new MailSendApi.Command("shop","registration",f.template.code(),"zh-CN","recipient@example.test",Map.of("code","123456"),now.plusSeconds(600),key);}

    @Test void enqueueSharesBusinessTransactionAndStoresOnlyEncryptedContent(){
        var f=fixture(931,10);var tx=new TransactionTemplate(manager);
        as(f.tenant,()->{
            assertThatThrownBy(()->tx.execute(s->{api.enqueue(command(f,"rollback"));throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
            assertThat(rows.selectCount(new LambdaQueryWrapper<MailDeliveryEntity>())).isZero();
            var view=api.enqueue(command(f,"committed"));var row=rows.selectById(view.id());
            assertThat(row.getPayloadEnvelope()).doesNotContain("123456","recipient@example.test");
            assertThat(view.state()).isEqualTo("QUEUED");assertThat(view.recipientMasked()).isEqualTo("r***@example.test");return null;
        });
    }
    @Test void concurrentSameKeyEnqueuesOneTaskAndChangedContentIsRejected()throws Exception{
        var f=fixture(932,10);var c=command(f,"same");
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Long> enqueue=()->as(f.tenant,()->api.enqueue(c).id());
            var results=pool.invokeAll(List.of(enqueue,enqueue));assertThat(results.get(0).get()).isEqualTo(results.get(1).get());
        }
        as(f.tenant,()->{
            assertThat(rows.selectCount(new LambdaQueryWrapper<MailDeliveryEntity>())).isEqualTo(1);
            var changed=new MailSendApi.Command(c.application(),c.scene(),c.templateCode(),c.language(),c.recipient(),Map.of("code","999999"),c.expiresAt(),c.idempotencyKey());
            assertThatThrownBy(()->api.enqueue(changed)).isInstanceOfSatisfying(BizException.class,e->assertThat(e.getErrorCode()).isEqualTo(MailErrorCode.IDEMPOTENCY_CONFLICT));return null;
        });
    }
    @Test void policyTenantAndActorOwnershipAreEnforced(){
        var f=fixture(933,10);var view=as(f.tenant,()->api.enqueue(command(f,"owned")));
        as(934,()->{assertThatThrownBy(()->status.get(view.id())).isInstanceOf(BizException.class);return null;});
        Users.as(principal(f.tenant,72),()->{assertThatThrownBy(()->api.cancel(view.id())).isInstanceOf(AccessDeniedException.class);return null;});
        var c=command(f,"denied");
        as(f.tenant,()->{assertThatThrownBy(()->api.enqueue(new MailSendApi.Command("other",c.scene(),c.templateCode(),c.language(),c.recipient(),c.variables(),c.expiresAt(),c.idempotencyKey())))
                .isInstanceOf(AccessDeniedException.class);return null;});
        assertThatThrownBy(()->api.enqueue(c)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void claimedCrashIsRetriedButSendingCrashBecomesUnknownAndRejectsLateWorker(){
        var f=fixture(935,10);var view=as(f.tenant,()->api.enqueue(command(f,"crash")));
        var claim=job(f.tenant,()->work.claim("first").orElseThrow());
        org.mockito.Mockito.doReturn(now.plusSeconds(121)).when(clock).now();
        job(f.tenant,()->{assertThat(work.recover()).isEqualTo(1);assertThat(work.begin(claim)).isEmpty();return null;});
        var second=job(f.tenant,()->work.claim("second").orElseThrow());
        var prepared=job(f.tenant,()->work.begin(second).orElseThrow());
        assertThat(prepared.payload().body()).isEqualTo("Code 123456");
        assertThat(prepared.profile().getConfigurationVersion()).isEqualTo(1);
        org.mockito.Mockito.doReturn(now.plusSeconds(242)).when(clock).now();
        job(f.tenant,()->{
            assertThat(work.recover()).isEqualTo(1);assertThat(work.finish(second,MailWorkService.Outcome.ACCEPTED)).isFalse();
            assertThat(work.claim("third")).isEmpty();return null;
        });
        assertThat(as(f.tenant,()->status.get(view.id()).state())).isEqualTo("UNKNOWN");
    }
    @Test void beginChecksLiveDisableAndRetainsFrozenTemplateAndProfile(){
        var f=fixture(936,10);var view=as(f.tenant,()->api.enqueue(command(f,"disabled")));
        var claim=job(f.tenant,()->work.claim("worker").orElseThrow());
        as(f.tenant,()->profiles.disable(f.profile.id(),f.profile.version()));
        assertThat(job(f.tenant,()->work.begin(claim))).isEmpty();
        assertThat(as(f.tenant,()->status.get(view.id()).state())).isEqualTo("CANCELLED");
    }
    @Test void quotaIsAtomicAndSafeRetryBackoffDoesNotSendUnknownAgain(){
        var f=fixture(937,1);var first=as(f.tenant,()->api.enqueue(command(f,"first")));
        var second=as(f.tenant,()->api.enqueue(command(f,"second")));
        var c=job(f.tenant,()->work.claim("a").orElseThrow());job(f.tenant,()->work.begin(c).orElseThrow());
        job(f.tenant,()->work.finish(c,MailWorkService.Outcome.RETRYABLE));
        var next=job(f.tenant,()->work.claim("b").orElseThrow());assertThat(next.deliveryId()).isEqualTo(second.id());
        assertThat(job(f.tenant,()->work.begin(next))).isEmpty();
        assertThat(as(f.tenant,()->status.get(second.id()).lastResult())).isEqualTo("RATE_LIMITED");
        org.mockito.Mockito.doReturn(now.plusSeconds(80)).when(clock).now();
        var retry=job(f.tenant,()->work.claim("a").orElseThrow());assertThat(retry.deliveryId()).isEqualTo(first.id());
        job(f.tenant,()->work.begin(retry).orElseThrow());job(f.tenant,()->work.finish(retry,MailWorkService.Outcome.UNKNOWN));
        assertThat(as(f.tenant,()->status.get(first.id()).state())).isEqualTo("UNKNOWN");
    }
    @Test void cancellationAndExpiryPreventWorkerUseAndEraseSensitiveMaterial(){
        var f=fixture(938,10);var view=as(f.tenant,()->api.enqueue(command(f,"cancel")));
        var c=job(f.tenant,()->work.claim("a").orElseThrow());as(f.tenant,()->api.cancel(view.id()));
        assertThat(job(f.tenant,()->work.begin(c))).isEmpty();
        var expired=as(f.tenant,()->api.enqueue(command(f,"expiry")));
        org.mockito.Mockito.doReturn(now.plusSeconds(601)).when(clock).now();job(f.tenant,()->work.recover());
        as(f.tenant,()->{assertThat(status.get(expired.id()).state()).isEqualTo("CANCELLED");assertThat(rows.selectById(expired.id()).getPayloadEnvelope()).isNull();return null;});
    }

    @Test void acceptedTaskErasesPayloadButSameKeyReplayReturnsOriginalReceipt(){
        var f=fixture(939,10);var command=command(f,"accepted");var receipt=as(f.tenant,()->api.enqueue(command));
        var claim=job(f.tenant,()->work.claim("worker").orElseThrow());job(f.tenant,()->work.begin(claim).orElseThrow());
        job(f.tenant,()->work.finish(claim,MailWorkService.Outcome.ACCEPTED));
        as(f.tenant,()->{
            assertThat(rows.selectById(receipt.id()).getPayloadEnvelope()).isNull();
            assertThat(api.enqueue(command).id()).isEqualTo(receipt.id());assertThat(api.enqueue(command).state()).isEqualTo("ACCEPTED");return null;
        });
    }

    @Test void queueReservationsPreventOversubscriptionAndReleaseOnCancel(){
        var f=fixture(940,10);
        as(f.tenant,()->{
            var first=api.enqueue(command(f,"one"));api.enqueue(command(f,"two"));
            assertThatThrownBy(()->api.enqueue(command(f,"three"))).isInstanceOfSatisfying(BizException.class,
                    e->assertThat(e.getErrorCode()).isEqualTo(MailErrorCode.QUOTA_EXCEEDED));
            api.cancel(first.id());assertThat(api.enqueue(command(f,"three")).state()).isEqualTo("QUEUED");return null;
        });
    }
    @Test void suspendedTenantCannotSendAnAlreadyClaimedTask(){
        var f=fixture(941,10);var receipt=as(f.tenant,()->api.enqueue(command(f,"suspended")));
        var claim=job(f.tenant,()->work.claim("worker").orElseThrow());
        org.mockito.Mockito.doReturn(Optional.of(new TenantEntryQuery.SecurityState(f.tenant,2,false,false))).when(tenants).securityState(f.tenant);
        assertThat(job(f.tenant,()->work.begin(claim))).isEmpty();
        assertThat(as(f.tenant,()->status.get(receipt.id()).lastResult())).isEqualTo("TENANT_DISABLED");
    }
    @Test void dispatchConnectsDurableWorkToTransportAndPersistsAcceptanceOutsideBusinessTransaction(){
        var f=fixture(942,10);var receipt=as(f.tenant,()->api.enqueue(command(f,"dispatch")));
        org.mockito.Mockito.doAnswer(i->{
            var prepared=i.getArgument(0,MailWorkService.Prepared.class);
            assertThat(prepared.claim().deliveryId()).isEqualTo(receipt.id());
            assertThat(prepared.payload().body()).isEqualTo("Code 123456");
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(ActorContext.tenantId()).isEqualTo(f.tenant);
            return MailWorkService.Outcome.ACCEPTED;
        }).when(sender).send(org.mockito.ArgumentMatchers.any());
        assertThat(dispatch.runTenant(f.tenant)).isEqualTo(1);
        assertThat(as(f.tenant,()->status.get(receipt.id()).state())).isEqualTo("ACCEPTED");
        assertThat(dispatch.runTenant(f.tenant)).isZero();
        org.mockito.Mockito.verify(sender,org.mockito.Mockito.times(1)).send(org.mockito.ArgumentMatchers.any());
    }

    private MailReceiptApi.Command receipt(Fixture f,long id,String event,MailReceiptApi.Type type){
        return new MailReceiptApi.Command("shop",f.profile.id(),"test-smtp",event,as(f.tenant,()->rows.selectById(id).getMessageId()),type,now);
    }
    private void accepted(Fixture f,long id){
        var claim=job(f.tenant,()->work.claim("worker").orElseThrow());assertThat(claim.deliveryId()).isEqualTo(id);
        job(f.tenant,()->work.begin(claim).orElseThrow());job(f.tenant,()->work.finish(claim,MailWorkService.Outcome.ACCEPTED));
    }
    @Test void receiptOrderingIsMonotonicAndSuppressionRejectsNewAndPreviouslyQueuedMail(){
        var f=fixture(943,10);var first=as(f.tenant,()->api.enqueue(command(f,"accepted")));accepted(f,first.id());
        var queued=as(f.tenant,()->api.enqueue(command(f,"queued")));
        var bounce=receipt(f,first.id(),"bounce",MailReceiptApi.Type.BOUNCED);
        assertThat(gateway(f.tenant,()->receipts.accept(bounce)).receiptState()).isEqualTo("BOUNCED");
        assertThat(gateway(f.tenant,()->receipts.accept(bounce)).duplicate()).isTrue();
        var late=receipt(f,first.id(),"late-delivery",MailReceiptApi.Type.DELIVERED);
        assertThat(gateway(f.tenant,()->receipts.accept(late)).receiptState()).isEqualTo("BOUNCED");
        var complaint=receipt(f,first.id(),"complaint",MailReceiptApi.Type.COMPLAINT);
        assertThat(gateway(f.tenant,()->receipts.accept(complaint)).receiptState()).isEqualTo("COMPLAINT");
        assertThat(gateway(f.tenant,()->receipts.accept(bounce)).receiptState()).isEqualTo("COMPLAINT");
        as(f.tenant,()->{
            assertThat(status.get(first.id()).receiptState()).isEqualTo("COMPLAINT");
            assertThat(rows.selectById(first.id()).getPayloadEnvelope()).isNull();
            assertThatThrownBy(()->api.enqueue(command(f,"blocked"))).isInstanceOfSatisfying(BizException.class,
                    e->assertThat(e.getErrorCode()).isEqualTo(MailErrorCode.RECIPIENT_SUPPRESSED));
            assertThat(suppressionRows.selectList(null)).hasSize(1);return null;
        });
        var claim=job(f.tenant,()->work.claim("worker").orElseThrow());assertThat(claim.deliveryId()).isEqualTo(queued.id());
        assertThat(job(f.tenant,()->work.begin(claim))).isEmpty();
        assertThat(as(f.tenant,()->status.get(queued.id()).lastResult())).isEqualTo("RECIPIENT_SUPPRESSED");
        // A separate profile in the same tenant has a separate recipient key and suppression scope.
        var otherProfile=fixture(943,10);assertThat(as(943,()->api.enqueue(command(otherProfile,"allowed")).state())).isEqualTo("QUEUED");
        var otherTenant=fixture(944,10);assertThat(as(944,()->api.enqueue(command(otherTenant,"allowed")).state())).isEqualTo("QUEUED");
    }
    @Test void receiptsRejectWrongIdentityTenantProfileAndChangedEventPayload(){
        var f=fixture(945,10);var task=as(f.tenant,()->api.enqueue(command(f,"accepted")));accepted(f,task.id());
        var c=receipt(f,task.id(),"event",MailReceiptApi.Type.DELIVERED);
        assertThatThrownBy(()->as(f.tenant,()->receipts.accept(c))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->Users.as(SmaPrincipal.forApplication(f.tenant,"admin","untrusted"),()->receipts.accept(c))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->gateway(946,()->receipts.accept(c))).isInstanceOf(BizException.class);
        var wrongProfile=new MailReceiptApi.Command(c.application(),c.profileId()+1,c.provider(),c.eventId(),c.messageId(),c.type(),now);
        assertThatThrownBy(()->gateway(f.tenant,()->receipts.accept(wrongProfile))).isInstanceOf(BizException.class);
        gateway(f.tenant,()->receipts.accept(c));
        var changed=receipt(f,task.id(),"event",MailReceiptApi.Type.BOUNCED);
        assertThatThrownBy(()->gateway(f.tenant,()->receipts.accept(changed))).isInstanceOfSatisfying(BizException.class,
                e->assertThat(e.getErrorCode()).isEqualTo(MailErrorCode.IDEMPOTENCY_CONFLICT));
        assertThat(as(f.tenant,()->status.get(task.id()).receiptState())).isEqualTo("DELIVERED");
    }
    @Test void receiptDuringSmtpAndUnknownRecoveryNeverCausesAutomaticResend(){
        var f=fixture(947,10);var task=as(f.tenant,()->api.enqueue(command(f,"sending")));
        var claim=job(f.tenant,()->work.claim("worker").orElseThrow());job(f.tenant,()->work.begin(claim).orElseThrow());
        var delivered=receipt(f,task.id(),"early",MailReceiptApi.Type.DELIVERED);gateway(f.tenant,()->receipts.accept(delivered));
        job(f.tenant,()->work.finish(claim,MailWorkService.Outcome.RETRYABLE));
        assertThat(as(f.tenant,()->status.get(task.id()).state())).isEqualTo("ACCEPTED");
        assertThat(job(f.tenant,()->work.claim("worker"))).isEmpty();
        var uncertain=as(f.tenant,()->api.enqueue(command(f,"uncertain")));
        var next=job(f.tenant,()->work.claim("worker").orElseThrow());job(f.tenant,()->work.begin(next).orElseThrow());
        job(f.tenant,()->work.finish(next,MailWorkService.Outcome.UNKNOWN));
        gateway(f.tenant,()->receipts.accept(receipt(f,uncertain.id(),"reconcile",MailReceiptApi.Type.DELIVERED)));
        assertThat(as(f.tenant,()->status.get(uncertain.id()).receiptState())).isEqualTo("DELIVERED");
        assertThat(as(f.tenant,()->status.get(uncertain.id()).state())).isEqualTo("UNKNOWN");
    }
    @Test void unsentAndOutOfWindowReceiptsAreRejectedAndLegacyPendingMailGetsIndexedBeforeSending(){
        var f=fixture(948,10);var task=as(f.tenant,()->api.enqueue(command(f,"legacy")));
        var c=receipt(f,task.id(),"unsent",MailReceiptApi.Type.DELIVERED);
        assertThatThrownBy(()->gateway(f.tenant,()->receipts.accept(c))).isInstanceOf(BizException.class);
        as(f.tenant,()->{rows.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MailDeliveryEntity>()
                .eq(MailDeliveryEntity::getId,task.id()).set(MailDeliveryEntity::getRecipientKey,null));return null;});
        accepted(f,task.id());assertThat(as(f.tenant,()->rows.selectById(task.id()).getRecipientKey())).hasSize(64);
        for(var time:List.of(now.plusSeconds(301),now.minus(Duration.ofDays(31)))){
            var bad=new MailReceiptApi.Command(c.application(),c.profileId(),c.provider(),c.eventId(),c.messageId(),c.type(),time);
            assertThatThrownBy(()->gateway(f.tenant,()->receipts.accept(bad))).isInstanceOf(BizException.class);
        }
    }
}
