package com.sma.admin.mail;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.internal.persistence.*;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@IntegrationTest
class MailTemplateTests {
    private static Path keyFile;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry) throws Exception {
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        keyFile = Files.createTempFile("sma-mail-templates-", ".keys");
        Files.writeString(keyFile, "active=test\nkey.test=" + Base64.getEncoder().encodeToString(key));
        registry.add("sma.common.credentials.key-file", () -> keyFile.toString());
    }
    @AfterAll static void cleanup() throws Exception { if (keyFile != null) Files.deleteIfExists(keyFile); }
    @Autowired MailTemplateService templates;
    @Autowired MailProfileService profiles;
    @Autowired MailTemplateVersionMapper versions;
    private <T>T as(long tenant, Supplier<T> action) {
        return Users.as(new SmaPrincipal(71,"template-admin",tenant,"admin",null,false,DataAccess.none(),
                Set.of("mail:profile:view","mail:profile:manage","mail:template:view","mail:template:manage","mail:template:publish"),"UTC"), action);
    }
    private String code() { return "t-" + UUID.randomUUID().toString().substring(0,8); }
    private MailProfileService.View profile(String code) {
        return profiles.create(new MailProfileService.Input(code,"SMTP","smtp.example.test",587,MailProfileService.TlsMode.STARTTLS,
                "mailer@example.test","password".toCharArray(),"sender@example.test","Sender",Set.of("shop"),100,10,true));
    }
    private MailTemplateService.Input input(String code, String profile, String body) {
        return new MailTemplateService.Input(code,"shop","registration","zh-CN",profile,
                new MailTemplateContent("欢迎",body,false,Map.of("name",new MailTemplateContent.Variable(MailTemplateContent.Type.TEXT,100,true)),Set.of()));
    }
    @Test void draftEditsNeverRewritePublishedPayloadOrAccountSnapshot() {
        as(921,()->{
            var profile=profile(code()); var draft=templates.create(input(code(),profile.code(),"Hello {{name}}"));
            assertThat(draft.publishedVersion()).isZero(); assertThat(draft.enabled()).isFalse();
            var published=templates.publish(draft.id(),1);
            var snapshot=versions.selectOne(new LambdaQueryWrapper<MailTemplateVersionEntity>().eq(MailTemplateVersionEntity::getTemplateId,draft.id()));
            assertThat(snapshot.getProfileId()).isEqualTo(profile.id()); assertThat(snapshot.getProfileVersion()).isEqualTo(1);
            String original=snapshot.getContentJson();
            var edited=templates.update(draft.id(),published.version(),input(draft.code(),profile.code(),"Changed {{name}}"));
            assertThat(edited.publishedVersion()).isEqualTo(published.publishedVersion());
            assertThat(versions.selectById(snapshot.getId()).getContentJson()).isEqualTo(original);
            assertThatThrownBy(()->templates.publish(draft.id(),published.version())).isInstanceOf(BizException.class);
            var second=templates.publish(draft.id(),edited.version());
            assertThat(second.publishedVersion()).isGreaterThan(published.publishedVersion());
            assertThat(versions.selectCount(new LambdaQueryWrapper<MailTemplateVersionEntity>().eq(MailTemplateVersionEntity::getTemplateId,draft.id()))).isEqualTo(2);
            assertThat(templates.disable(draft.id(),second.version()).enabled()).isFalse(); return null;
        });
    }
    @Test void tenantCanUseSameCodeButCannotReadPublishOrResolveForeignConfiguration() {
        String code=code(),profileCode=code();
        var a=as(922,()->{profile(profileCode);return templates.create(input(code,profileCode,"A {{name}}"));});
        as(923,()->{
            var b=templates.create(input(code,profileCode,"B {{name}}"));
            assertThatThrownBy(()->templates.get(a.id())).isInstanceOf(BizException.class);
            assertThatThrownBy(()->templates.publish(a.id(),1)).isInstanceOf(BizException.class);
            assertThatThrownBy(()->templates.publish(b.id(),1)).isInstanceOf(BizException.class);
            assertThat(templates.page(new PageQuery(1,20,null,null),null).total()).isEqualTo(1);
            profile(profileCode); assertThat(templates.publish(b.id(),1).enabled()).isTrue(); return null;
        });
    }
    @Test void publicationChecksCurrentAccountAndApplicationGrantWithoutChangingDraft() {
        as(924,()->{
            var p=profile(code()); var d=templates.create(input(code(),p.code(),"{{name}}"));
            profiles.disable(p.id(),1);
            assertThatThrownBy(()->templates.publish(d.id(),1)).isInstanceOf(BizException.class);
            assertThat(templates.get(d.id()).publishedVersion()).isZero();
            assertThatThrownBy(()->templates.update(d.id(),1,input(d.code(),p.code(),"{{name.toString()}}")))
                    .isInstanceOf(BizException.class);
            return null;
        });
    }
}
