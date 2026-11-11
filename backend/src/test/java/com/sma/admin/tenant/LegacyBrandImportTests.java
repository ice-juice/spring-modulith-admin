package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.config.LegacyBrandSource;
import com.sma.admin.config.internal.service.ParameterService;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.service.*;
import com.sma.admin.tenant.internal.persistence.*;

@IntegrationTest
class LegacyBrandImportTests {
    private final List<Long> seededIds=new ArrayList<>();
    @Autowired JdbcTemplate jdbc;
    @Autowired LegacyBrandMigrationService migration;
    @Autowired LegacyBrandSource source;
    @Autowired BrandDraftService drafts;
    @Autowired BrandPublicationService publications;
    @Autowired BrandEntryService entry;
    @Autowired BrandHeadMapper heads;
    @Autowired BrandRevisionMapper revisions;
    @Autowired BrandImportMapper imports;
    @Autowired ParameterService parameters;
    @Autowired com.sma.admin.config.internal.service.DefinedParameterService parameterLayers;
    @Autowired PlatformTransactionManager transactions;
    private static SmaPrincipal actor(long tenant,String portal) {
        return new SmaPrincipal(913,"migration-editor",tenant,portal,null,false,DataAccess.own(),
                Set.of("tenant:brand:view","tenant:brand:edit","tenant:brand:publish","tenant:brand:rollback"),"UTC");
    }
    private void register(long tenant,String portal) { Users.as(actor(tenant,portal),()->null); }
    private void seed(long tenant,String key,String value,boolean publicly,boolean secret) {
        long id=IdWorker.getId();
        jdbc.update("""
            INSERT INTO config_parameter(id,tenant_id,param_key,group_key,name,value_type,param_value,builtin,public_readable,secret,sort,
                created_at,created_by,updated_at,updated_by,deleted)
            VALUES (?,?,?,'basic',?,?,?,1,?,?,0,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
            """,id,tenant,key,key,key.equals("login.captchaEnabled")?"BOOLEAN":"STRING",value,publicly,secret);
        seededIds.add(id);
    }
    @org.junit.jupiter.api.AfterEach void removeOnlyThisTestSourceFixtures() {
        // Rejected upgrade fixtures must not poison a later test class's ApplicationReady bootstrap.
        for(long id:seededIds) jdbc.update("DELETE FROM config_parameter WHERE id=?",id);
    }
    private <T>T background(long tenant,Supplier<T> action) {
        var security=SecurityContextHolder.getContext();var request=RequestContextHolder.getRequestAttributes();
        SecurityContextHolder.clearContext();RequestContextHolder.resetRequestAttributes();
        try { return TenantScope.within(tenant,action); }
        finally { SecurityContextHolder.setContext(security);if(request!=null)RequestContextHolder.setRequestAttributes(request); }
    }
    private BrandEntryView published(long tenant,String portal) {
        try(var scope=TenantEntryScope.open(new TenantEntry(tenant,1,"t"+tenant+".test",portal,1))) { return entry.entry(); }
    }

    @Test void importsAllPortalsAtomicallyAndArchivesWithoutLosingLegacyBackgroundOrRollback() {
        long tenant=7610;register(tenant,"admin");register(tenant,"partner");
        seed(tenant,"system.name","Customer console",true,false);seed(tenant,"system.copyrightCompany","Customer Ltd",true,false);
        seed(tenant,"system.recordInfo","Customer ICP",true,false);seed(tenant,"login.backgroundUrl","/images/old.png",true,false);
        seed(tenant,"login.backgroundBlur","true",true,false);seed(tenant,"login.captchaEnabled","false",true,false);
        background(tenant,parameterLayers::migrateCurrentTenant);
        var result=background(tenant,migration::migrateCurrentTenant);assertThat(result.imported()).isTrue();
        assertThat(background(tenant,migration::migrateCurrentTenant).replay()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM config_parameter WHERE tenant_id=? AND brand_archived=1",Long.class,tenant)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT param_value FROM config_parameter WHERE tenant_id=? AND param_key='login.backgroundUrl'",String.class,tenant)).isEqualTo("/images/old.png");
        assertThat(background(tenant,()->revisions.selectCount(null))).isEqualTo(2);
        for(String portal:List.of("admin","partner")) {
            var view=published(tenant,portal);assertThat(view.branding().identity().systemName()).isEqualTo("Customer console");
            assertThat(view.branding().legal().copyrightOwner()).isEqualTo("Customer Ltd");
            assertThat(view.branding().legacyBackgroundUrl()).isEqualTo("/images/old.png");assertThat(view.login().captchaEnabled()).isFalse();
        }
        Users.as(actor(tenant,"admin"),()->{
            var first=drafts.get();var base=first.content();
            var hidden=new BrandContent(1,base.identity(),base.login(),base.legal(),base.links(),base.appearance(),base.assets(),Map.of(BrandContent.Field.LOGIN_BACKGROUND,BrandContent.OverrideMode.HIDE));
            drafts.save(first.version(),hidden);assertThat(drafts.get().legacyBackgroundUrl()).isNull();
            assertThat(publications.get(first.publishedRevisionId()).legacyBackgroundUrl()).isEqualTo("/images/old.png");
            var second=publications.publish(UUID.randomUUID().toString(),2,first.publishedRevisionId());
            assertThat(publications.get(second.revisionId()).legacyBackgroundUrl()).isNull();
            var restored=publications.rollback(UUID.randomUUID().toString(),first.publishedRevisionId(),second.revisionId());
            assertThat(publications.get(restored.revisionId()).legacyBackgroundUrl()).isEqualTo("/images/old.png");
            assertThat(parameters.list(null,null)).noneMatch(p->p.paramKey().equals("system.name"));
            assertThatThrownBy(()->parameters.stringValue("system.name","")).isInstanceOf(BizException.class);
            return null;
        });
    }

    @Test void laterPortalConflictRollsBackEarlierPortalPublicationAndSourceArchive() {
        long tenant=7611;register(tenant,"admin");register(tenant,"partner");seed(tenant,"system.name","Legacy",true,false);
        Users.as(actor(tenant,"partner"),()->drafts.save(0,BrandContent.defaults("Do not overwrite")));
        assertThatThrownBy(()->background(tenant,migration::migrateCurrentTenant)).isInstanceOf(IllegalStateException.class);
        assertThat(background(tenant,()->heads.selectCount(null))).isEqualTo(1);assertThat(background(tenant,()->revisions.selectCount(null))).isZero();
        assertThat(background(tenant,()->imports.selectCount(null))).isZero();
        assertThat(jdbc.queryForObject("SELECT brand_archived FROM config_parameter WHERE tenant_id=? AND param_key='system.name'",Boolean.class,tenant)).isFalse();
        Users.as(actor(tenant,"partner"),()->{assertThat(drafts.get().content().identity().systemName()).isEqualTo("Do not overwrite");return null;});
    }

    @Test void privateAndSecretSourcesAreArchivedButNeverPublishedOrLoggedAndHttpActorsCannotMigrate() {
        long tenant=7612;register(tenant,"admin");seed(tenant,"system.name","secret-brand-value",true,true);
        seed(tenant,"system.copyrightCompany","private-owner-value",false,false);
        var snapshot=background(tenant,()->new TransactionTemplate(transactions).execute(status->source.snapshot()));
        assertThat(snapshot.values().get(LegacyBrandSource.Key.NAME).text()).isNull();
        assertThat(snapshot.toString()).doesNotContain("secret-brand-value","private-owner-value");
        Users.as(actor(tenant,"admin"),()->{
            assertThatThrownBy(migration::migrateCurrentTenant).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);return null;
        });
        background(tenant,migration::migrateCurrentTenant);
        var view=published(tenant,"admin");assertThat(view.branding().identity().systemName()).isEqualTo("Synthetic test tenant "+tenant);
        assertThat(view.branding().legal().copyrightOwner()).isEmpty();
        var receipt=background(tenant,()->imports.selectOne(null));assertThat(receipt.getExcludedKeysJson()).contains("NAME","COPYRIGHT_OWNER").doesNotContain("secret-brand-value","private-owner-value");
    }

    @Test void invalidSourceAndChangedFingerprintCannotLeavePartialMigration() {
        long tenant=7613;register(tenant,"admin");seed(tenant,"system.name","Original",true,false);seed(tenant,"login.backgroundUrl","//unsafe.example/a",true,false);
        assertThatThrownBy(()->background(tenant,migration::migrateCurrentTenant)).isInstanceOf(BizException.class);
        assertThat(background(tenant,()->heads.selectCount(null))).isZero();
        assertThatThrownBy(()->background(tenant,()->new TransactionTemplate(transactions).execute(status->{
            var snapshot=source.snapshot();jdbc.update("UPDATE config_parameter SET param_value='Changed' WHERE tenant_id=? AND param_key='system.name'",tenant);
            source.archive(snapshot.sourceRevision());return null;
        }))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT param_value FROM config_parameter WHERE tenant_id=? AND param_key='system.name'",String.class,tenant)).isEqualTo("Original");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM config_parameter WHERE tenant_id=? AND brand_archived=1",Long.class,tenant)).isZero();
    }

    @Test void concurrentHostUpgradesProduceOneReceiptAndOnePublication() throws Exception {
        long tenant=7614;register(tenant,"admin");seed(tenant,"system.name","One import",true,false);
        var gate=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var jobs=java.util.stream.IntStream.range(0,2).mapToObj(i->executor.submit(()->{
                gate.await();return background(tenant,migration::migrateCurrentTenant);
            })).toList();gate.countDown();
            var results=List.of(jobs.get(0).get(30,java.util.concurrent.TimeUnit.SECONDS),jobs.get(1).get(30,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(results.stream().filter(LegacyBrandMigrationService.Result::imported).count()).isEqualTo(1);
            assertThat(results.stream().filter(LegacyBrandMigrationService.Result::replay).count()).isEqualTo(1);
        }
        assertThat(background(tenant,()->imports.selectCount(null))).isEqualTo(1);
        assertThat(background(tenant,()->revisions.selectCount(null))).isEqualTo(1);
    }
}
