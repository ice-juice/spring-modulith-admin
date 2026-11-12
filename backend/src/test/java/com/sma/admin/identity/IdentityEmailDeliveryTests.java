package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.persistence.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.identity.internal.service.IdentityEmailChallengeStore.*;
import com.sma.admin.mail.*;
import com.sma.admin.mail.internal.persistence.*;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.*;

@IntegrationTest
class IdentityEmailDeliveryTests {
    private static <T>T host(Supplier<T> action){
        var request=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        try{return action.get();}finally{org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(request);}
    }
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        byte[] value=new byte[32];new SecureRandom().nextBytes(value);keyFile=Files.createTempFile("sma-email-delivery-",".keys");
        Files.writeString(keyFile,"active=test\nkey.test="+Base64.getEncoder().encodeToString(value));registry.add("sma.common.credentials.key-file",()->keyFile.toString());
    }
    @AfterAll static void cleanup()throws Exception{if(keyFile!=null)Files.deleteIfExists(keyFile);}
    @Autowired IdentityRealmService realms;@Autowired IdentityClientService clients;@Autowired IdentityEmailConfigurationService configurations;
    @Autowired IdentityEmailDeliveryService delivery;@Autowired IdentityEmailChallengeStore challenges;@Autowired IdentityEmailChallengeMapper challengeRows;
    @Autowired MailProfileService profiles;@Autowired MailTemplateService templates;@Autowired MailDeliveryMapper mailRows;@Autowired MailProfileMapper profileRows;
    @Autowired MailSendApi mail;@Autowired MailOutboxService outbox;@Autowired MailWorkService work;@Autowired MailDispatchCycle cycle;@Autowired TransactionTemplate tx;
    @MockitoSpyBean IdentityClock clock;@MockitoSpyBean MailWorkClock mailClock;@MockitoSpyBean SmtpMailSender sender;
    private Instant now;private final String origin="https://app.example.test";
    @BeforeEach void time(){now=Instant.parse("2026-11-11T00:00:00.123456789Z");doAnswer(i->now).when(clock).now();doAnswer(i->now).when(mailClock).now();}
    private <T>T job(long tenant,Supplier<T> action){return ActorContext.withinJob(tenant,"identity-email-delivery-test",action);}
    private <T>T admin(long tenant,Supplier<T> action){return Users.as(new SmaPrincipal(151,"email-delivery-admin",tenant,"admin",null,false,DataAccess.none(),Set.of(
            "identity:realm:manage","identity:client:manage","identity:client:view","mail:profile:manage","mail:template:manage","mail:template:publish","mail:template:view"),"UTC"),action);}
    private record Fixture(long tenant,IdentityClientService.View client,MailProfileService.View profile,MailTemplateService.View registration){}
    private Fixture fixture(long tenant){return admin(tenant,()->{
        var realm=realms.create(new IdentityRealmService.Input("email","Email",true,true,true));
        var client=clients.create(new IdentityClientService.Input(realm.id(),"email","Email","shop",IdentityClientService.Platform.WEB,true,false,null,null,
                Set.of(origin+"/cb"),Set.of(origin),900,2592000));
        var profile=profiles.create(new MailProfileService.Input("email","SMTP","smtp.example.test",587,MailProfileService.TlsMode.STARTTLS,
                "mailer@example.test","smtp-test-password".toCharArray(),"sender@example.test","Sender",Set.of("identity"),100,10,true));
        var content=new MailTemplateContent("Verification","Code {{code}}, valid {{minutes}} minutes",false,
                Map.of("code",new MailTemplateContent.Variable(MailTemplateContent.Type.TEXT,6,true),"minutes",new MailTemplateContent.Variable(MailTemplateContent.Type.INTEGER,2,true)),Set.of());
        MailTemplateService.View registration=null;
        for(String scene:List.of("registration","password-reset","email-binding")){
            var draft=templates.create(new MailTemplateService.Input(scene,"identity",scene,"en-US",profile.code(),content));var published=templates.publish(draft.id(),draft.version());
            if(scene.equals("registration"))registration=published;
        }
        configurations.put(client.id(),"en-US",0,new IdentityEmailConfigurationService.Input(true,"registration","password-reset","email-binding"));
        return new Fixture(tenant,client,profile,registration);
    });}
    private Start start(Fixture f,String address){return new Start(f.client.loginHandle(),"en-US",Purpose.REGISTRATION,address,null,null,origin,"127.0.0.1");}
    private IdentityEmailDeliveryService.Issued issue(Fixture f,String address){return job(f.tenant,()->delivery.create(start(f,address)));}
    private MailDeliveryEntity task(long tenant){return job(tenant,()->mailRows.selectOne(new LambdaQueryWrapper<MailDeliveryEntity>()));}
    private IdentityEmailChallengeEntity challenge(long tenant){return job(tenant,()->challengeRows.selectOne(new LambdaQueryWrapper<IdentityEmailChallengeEntity>()));}
    private String code(MailPayload payload){var matcher=Pattern.compile("Code ([0-9]{6}),").matcher(payload.body());assertThat(matcher.find()).isTrue();return matcher.group(1);}
    private Result verify(Fixture f,IdentityEmailDeliveryService.Issued issued,String code){return job(f.tenant,()->challenges.verify(new Verification(issued.handle(),issued.proof(),code,f.client.loginHandle(),Purpose.REGISTRATION,origin,"127.0.0.1")));}
    @Test void creationAndMailCommitTogetherAndLocalDispatcherSendsOnlyOutsideTransaction(){
        var f=fixture(1161);var issued=issue(f,"recipient@example.test");var task=task(f.tenant);var challenge=challenge(f.tenant);
        assertThat(task.getGuardOwner()).isEqualTo(IdentityEmailMailPolicy.OWNER);assertThat(task.getGuardReference()).isEqualTo(challenge.getId().toString());
        assertThat(challenge.getDeliveryId()).isEqualTo(task.getId());assertThat(challenge.getStatus()).isEqualTo("PENDING");assertThat(issued.expiresAt()).isEqualTo(task.getExpiresAt());
        String[] sentCode=new String[1];doAnswer(i->{
            var prepared=(MailWorkService.Prepared)i.getArgument(0);assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(ActorContext.tenantId()).isEqualTo(f.tenant);assertThat(prepared.payload().recipient()).isEqualTo("recipient@example.test");sentCode[0]=code(prepared.payload());i.getArgument(1,com.sma.admin.common.deployment.DeploymentWork.Permit.class).close();return MailWorkService.Outcome.ACCEPTED;
        }).when(sender).send(any(),any());
        assertThat(host(()->cycle.runTenant(f.tenant))).isEqualTo(1);assertThat(task(f.tenant).getState()).isEqualTo("ACCEPTED");assertThat(task(f.tenant).getPayloadEnvelope()).isNull();
        assertThat(verify(f,issued,sentCode[0]).error()).isNull();org.mockito.Mockito.verify(sender,times(1)).send(any(),any());
    }
    @Test void outerRollbackAndUnavailableTemplateLeaveNeitherChallengeNorMail(){
        var f=fixture(1162);
        assertThatThrownBy(()->job(f.tenant,()->tx.execute(status->{delivery.create(start(f,"rollback@example.test"));throw new IllegalStateException("business rollback");}))).isInstanceOf(IllegalStateException.class);
        assertThat(task(f.tenant)).isNull();assertThat(challenge(f.tenant)).isNull();
        admin(f.tenant,()->templates.disable(f.registration.id(),f.registration.version()));
        assertThatThrownBy(()->issue(f,"unavailable@example.test")).isInstanceOf(BizException.class);
        assertThat(task(f.tenant)).isNull();assertThat(challenge(f.tenant)).isNull();assertThat(job(f.tenant,()->profileRows.selectById(f.profile.id())).getPendingDeliveries()).isZero();
    }
    @Test void identitySceneCannotQueueArbitraryRecipientCodeOrUnguardedMessage(){
        var f=fixture(1163);
        for(int variant=0;variant<3;variant++){
            int fault=variant;
            assertThatThrownBy(()->job(f.tenant,()->tx.execute(status->{try(var prepared=challenges.begin(start(f,"owned@example.test"))){
                String reference=Long.toString(prepared.challengeId());char[] raw=prepared.code().copy();
                try{
                    String actual=new String(raw),wrong=actual.equals("000000")?"111111":"000000";
                    var command=new MailSendApi.Command("identity",prepared.scene(),prepared.templateCode(),prepared.language(),fault==0?"attacker@example.test":prepared.address(),
                            Map.of("code",fault==1?wrong:actual,"minutes","5"),prepared.expiresAt(),"identity-email:"+reference,
                            fault==2?null:new MailSendApi.Guard(IdentityEmailMailPolicy.OWNER,reference));
                    return ActorContext.withinJob(f.tenant,"identity-email:"+reference,()->mail.enqueue(command));
                }finally{Arrays.fill(raw,'\0');}
            }}))).isInstanceOf(AccessDeniedException.class);
        }
        assertThat(task(f.tenant)).isNull();assertThat(challenge(f.tenant)).isNull();
    }
    @Test void challengeUsedBeforeClaimOrAfterPreparationBlocksTransport(){
        var f=fixture(1164);var issued=issue(f,"used@example.test");var row=task(f.tenant);String rawCode=job(f.tenant,()->code(outbox.payload(row)));
        assertThat(verify(f,issued,rawCode).error()).isNull();
        job(f.tenant,()->{var claim=work.claim("before").orElseThrow();assertThat(work.begin(claim)).isEmpty();return null;});
        assertThat(task(f.tenant).getState()).isEqualTo("CANCELLED");assertThat(task(f.tenant).getLastResult()).isEqualTo("BUSINESS_INVALIDATED");
        var second=fixture(1165);var another=issue(second,"later@example.test");
        job(second.tenant,()->{
            var claim=work.claim("after").orElseThrow();var prepared=work.begin(claim).orElseThrow();assertThat(work.stillValid(claim)).isTrue();
            assertThat(verify(second,another,code(prepared.payload())).error()).isNull();assertThat(work.stillValid(claim)).isFalse();
            assertThat(work.finish(claim,MailWorkService.Outcome.CANCELLED)).isTrue();return null;
        });verifyNoInteractions(sender);assertThat(task(second.tenant).getPayloadEnvelope()).isNull();
    }
    @Test void configurationRevocationAndUnknownGateFailClosedAndOtherTenantCannotClaim(){
        var f=fixture(1166);issue(f,"revoked@example.test");
        admin(f.tenant,()->configurations.put(f.client.id(),"en-US",1,new IdentityEmailConfigurationService.Input(false,"registration","password-reset","email-binding")));
        assertThat(host(()->cycle.runTenant(f.tenant))).isZero();assertThat(task(f.tenant).getState()).isEqualTo("CANCELLED");verifyNoInteractions(sender);
        var second=fixture(1167);issue(second,"missing-gate@example.test");var row=task(second.tenant);
        job(second.tenant,()->{row.setGuardOwner("uninstalled.owner");mailRows.updateById(row);return null;});
        assertThat(host(()->cycle.runTenant(second.tenant))).isZero();assertThat(task(second.tenant).getState()).isEqualTo("CANCELLED");
        assertThat(job(1168,()->work.claim("wrong-tenant"))).isEmpty();
        assertThat(job(second.tenant,()->profileRows.selectById(second.profile.id())).getPendingDeliveries()).isZero();
    }
    @Test void expiryBetweenPreparationAndTransportDoesNotSendExpiredCode(){
        var f=fixture(1169);issue(f,"expiry@example.test");
        job(f.tenant,()->{var claim=work.claim("expiry").orElseThrow();work.begin(claim).orElseThrow();now=now.plusSeconds(301);
            assertThat(work.stillValid(claim)).isFalse();work.finish(claim,MailWorkService.Outcome.CANCELLED);return null;});
        verifyNoInteractions(sender);assertThat(task(f.tenant).getState()).isEqualTo("CANCELLED");
    }
}
