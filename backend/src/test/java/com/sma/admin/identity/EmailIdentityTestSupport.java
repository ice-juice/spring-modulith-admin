package com.sma.admin.identity;
import java.util.*;
import java.util.function.Supplier;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.mail.*;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.support.Users;

/** Plain fixture helper, never a scanned Spring component. All configuration goes through real permission-checked services. */
final class EmailIdentityTestSupport {
    static final String ORIGIN="https://app.example.test";
    record Fixture(long tenant,IdentityRealmService.View realm,IdentityClientService.View client){}
    static final class Mailbox {
        private final IdentityEmailAccountService accounts;private final IdentityEmailChallengeStore challenges;
        private final com.sma.admin.identity.internal.persistence.IdentityEmailChallengeMapper rows;
        private final com.sma.admin.mail.internal.persistence.MailDeliveryMapper deliveries;private final MailOutboxService outbox;
        Mailbox(IdentityEmailAccountService accounts,IdentityEmailChallengeStore challenges,com.sma.admin.identity.internal.persistence.IdentityEmailChallengeMapper rows,
                com.sma.admin.mail.internal.persistence.MailDeliveryMapper deliveries,MailOutboxService outbox){this.accounts=accounts;this.challenges=challenges;this.rows=rows;this.deliveries=deliveries;this.outbox=outbox;}
        IdentityEmailChallengeStore.Use grant(Fixture f,String address,IdentityEmailChallengeStore.Purpose purpose){
            var issued=job(f.tenant(),()->accounts.start(new IdentityEmailAccountService.Challenge(f.client().loginHandle(),"en-US",purpose,address,ORIGIN,"127.0.0.1")));
            return verify(f,issued,purpose);
        }
        IdentityEmailChallengeStore.Use verify(Fixture f,IdentityEmailDeliveryService.Issued issued,IdentityEmailChallengeStore.Purpose purpose){
            String hash;try{hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(issued.handle().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));}
            catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
            var row=job(f.tenant(),()->rows.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sma.admin.identity.internal.persistence.IdentityEmailChallengeEntity>()
                    .eq(com.sma.admin.identity.internal.persistence.IdentityEmailChallengeEntity::getHandleHash,hash)));
            String body=job(f.tenant(),()->outbox.payload(deliveries.selectById(row.getDeliveryId())).body());var matcher=java.util.regex.Pattern.compile("Code ([0-9]{6}),").matcher(body);
            if(!matcher.find())throw new AssertionError("Verification fixture has no code");String code=matcher.group(1);
            var result=job(f.tenant(),()->challenges.verify(new IdentityEmailChallengeStore.Verification(issued.handle(),issued.proof(),code,f.client().loginHandle(),purpose,ORIGIN,"127.0.0.1")));
            if(result.error()!=null)throw new AssertionError("Verification fixture rejected: "+result.error());
            return new IdentityEmailChallengeStore.Use(result.grant(),issued.proof(),f.client().loginHandle(),purpose,ORIGIN);
        }
        IdentitySessionStore.Issued register(Fixture f,String address,String password){var grant=grant(f,address,IdentityEmailChallengeStore.Purpose.REGISTRATION);return job(f.tenant(),()->accounts.register(grant,password));}
    }
    static <T>T admin(long tenant,Supplier<T> action){return Users.as(new SmaPrincipal(161,"email-account-admin",tenant,"admin",null,false,DataAccess.none(),Set.of(
            "identity:realm:manage","identity:client:manage","identity:client:view","mail:profile:manage","mail:template:manage","mail:template:publish","mail:template:view"),"UTC"),action);}
    static <T>T job(long tenant,Supplier<T> action){return ActorContext.withinJob(tenant,"identity-email-account-test",action);}
    static Fixture fixture(long tenant,IdentityRealmService realms,IdentityClientService clients,IdentityEmailConfigurationService configurations,MailProfileService profiles,MailTemplateService templates){
        return admin(tenant,()->{
            var realm=realms.create(new IdentityRealmService.Input("email","Email",true,true,true));
            var client=clients.create(new IdentityClientService.Input(realm.id(),"email","Email","shop",IdentityClientService.Platform.WEB,true,true,
                    "123456-email.apps.googleusercontent.com","google-test-secret-value".toCharArray(),Set.of(ORIGIN+"/cb"),Set.of(ORIGIN),900,2592000));
            var profile=profiles.create(new MailProfileService.Input("email","SMTP","smtp.example.test",587,MailProfileService.TlsMode.STARTTLS,
                    "mailer@example.test","smtp-test-password".toCharArray(),"sender@example.test","Sender",Set.of("identity"),100,10,true));
            var content=new MailTemplateContent("Verification","Code {{code}}, valid {{minutes}} minutes",false,
                    Map.of("code",new MailTemplateContent.Variable(MailTemplateContent.Type.TEXT,6,true),"minutes",new MailTemplateContent.Variable(MailTemplateContent.Type.INTEGER,2,true)),Set.of());
            for(String scene:List.of("registration","password-reset","email-binding")){
                var draft=templates.create(new MailTemplateService.Input(scene,"identity",scene,"en-US",profile.code(),content));templates.publish(draft.id(),draft.version());
            }
            configurations.put(client.id(),"en-US",0,new IdentityEmailConfigurationService.Input(true,"registration","password-reset","email-binding"));
            return new Fixture(tenant,realm,client);
        });
    }
}
