package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.*;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;
import com.sma.admin.config.internal.service.SharedConfigurationService.DictionaryContent;
import com.sma.admin.support.*;

@IntegrationTest @Import(DictionaryCodeRegistryTests.CatalogConfiguration.class)
@TestPropertySource(properties="sma.tenant.platform-admin-user-ids=991")
class DictionaryCodeRegistryTests {
    @Autowired DictionaryCodeRegistry registry;
    @Autowired SharedConfigurationService shared;
    @Autowired SharedHeadMapper heads;
    @Autowired DictionaryCodeMapper codes;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    private static final SmaPrincipal OPERATOR=new SmaPrincipal(991,"dictionary-operator",1,"admin",null,false,DataAccess.unrestricted(),
            Set.of("config:shared:view","config:shared:edit","config:shared:publish"),"UTC");
    private static ConfigurationLabel label(String text){return new ConfigurationLabel(text,null,Map.of());}
    private static DictionaryDefinition.Item item(String code,String parent){return new DictionaryDefinition.Item(code,parent,label(code),true,0);}
    private static DictionaryDefinition dictionary(String key,DictionaryDefinition.Mode mode){
        return new DictionaryDefinition("config",key,label(key),mode,true,List.of(item("root",null),item("child","root")));
    }
    record Catalog(List<DictionaryDefinition> dictionaries) implements ConfigurationCatalog {}
    @TestConfiguration(proxyBeanMethods=false) static class CatalogConfiguration {
        @Bean ConfigurationCatalog dictionaryCodeCatalog(){return new Catalog(List.of(
                dictionary("config_code_conflict",DictionaryDefinition.Mode.EXTENDABLE),
                dictionary("config_code_rollback",DictionaryDefinition.Mode.EXTENDABLE),
                dictionary("config_code_race",DictionaryDefinition.Mode.EXTENDABLE),
                dictionary("config_code_capacity",DictionaryDefinition.Mode.EXTENDABLE),
                dictionary("config_code_backfill",DictionaryDefinition.Mode.EXTENDABLE),
                dictionary("config_code_locked",DictionaryDefinition.Mode.PLATFORM_LOCKED)));}
    }
    private <T>T tenant(long tenant,Supplier<T> work){
        return TenantScope.within(tenant,()->new TransactionTemplate(transactions).execute(status->work.get()));
    }
    private void privateCode(long tenant,String dictionary,String code){tenant(tenant,()->{registry.reservePrivate(dictionary,code);return null;});}
    private SharedHeadEntity head(String key){return tenant(1,()->heads.lockKey(Kind.DICTIONARY.name(),key));}
    private long rows(long head){return codes.selectCount(new LambdaQueryWrapper<DictionaryCodeEntity>().eq(DictionaryCodeEntity::getHeadId,head));}
    private SharedConfigurationService.Detail detail(String key){
        return Users.as(OPERATOR,()->shared.get(shared.page(new PageQuery(1,20,null,null),Kind.DICTIONARY,key).items().getFirst().id()));
    }
    private ErrorCode error(Runnable work){
        try{work.run();}catch(BizException failure){return failure.getErrorCode();}
        throw new AssertionError("Expected business rejection");
    }
    private <T>T host(Supplier<T> work){
        try(var pool=Executors.newSingleThreadExecutor()){return pool.submit(work::get).get(30,TimeUnit.SECONDS);}
        catch(ExecutionException failure){if(failure.getCause() instanceof RuntimeException cause)throw cause;throw new IllegalStateException(failure);}
        catch(Exception failure){throw new IllegalStateException(failure);}
    }

    @Test void sharedAndPrivateScopesCannotSwapCodesAndRejectedPublicationLeavesNoClaim() {
        String key="config_code_conflict";var head=head(key);
        assertThat(head.getDictionaryCodeCount()).isEqualTo(2);assertThat(rows(head.getId())).isEqualTo(2);
        assertThat(error(()->privateCode(7401,key,"child"))).isEqualTo(ConfigErrorCode.DICTIONARY_CODE_CONFLICT);
        privateCode(7401,key,"tenant-only");privateCode(7402,key,"tenant-only");privateCode(7401,key,"tenant-only");
        privateCode(7402,key,"TENANT-ONLY"); // Exact strings: case variants are separate stable codes.
        assertThat(head(key).getDictionaryCodeCount()).isEqualTo(4);
        var before=detail(key);
        Users.as(OPERATOR,()->{
            var draft=shared.saveDictionary(before.id(),before.draftVersion(),new DictionaryContent(1,label("Next"),
                    List.of(item("root",null),item("child","root"),item("tenant-only","root"),item("added","root"))));
            assertThat(error(()->shared.publish(before.id(),draft.draftVersion(),draft.publishedRevisionId()))).isEqualTo(ConfigErrorCode.DICTIONARY_CODE_CONFLICT);
            assertThat(shared.get(before.id()).publishedRevisionId()).isEqualTo(before.publishedRevisionId());return null;
        });
        assertThat(head(key).getDictionaryCodeCount()).isEqualTo(4);assertThat(rows(before.id())).isEqualTo(4);
        Users.as(OPERATOR,()->{
            var current=shared.get(before.id());
            var draft=shared.saveDictionary(before.id(),current.draftVersion(),new DictionaryContent(1,label("Next"),
                    List.of(item("root",null),item("child","root"),item("added","root"))));
            shared.publish(before.id(),draft.draftVersion(),draft.publishedRevisionId());return null;
        });
        assertThat(head(key).getDictionaryCodeCount()).isEqualTo(5);
        assertThat(error(()->privateCode(7403,key,"added"))).isEqualTo(ConfigErrorCode.DICTIONARY_CODE_CONFLICT);
    }
    @Test void claimsJoinTheCallerTransactionAndLockedOrUnregisteredDictionariesRejectPrivateCodes() {
        String key="config_code_rollback";long head=head(key).getId();
        assertThatThrownBy(()->TenantScope.within(7404,()->{registry.reservePrivate(key,"outside");return null;})).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(()->tenant(7404,()->{registry.reservePrivate(key,"rolled-back");throw new IllegalStateException("fixture rollback");}))
                .isInstanceOf(IllegalStateException.class);
        assertThat(rows(head)).isEqualTo(2);assertThat(head(key).getDictionaryCodeCount()).isEqualTo(2);
        privateCode(7405,key,"rolled-back");assertThat(head(key).getDictionaryCodeCount()).isEqualTo(3);
        assertThat(error(()->privateCode(7404,"config_code_locked","private"))).isEqualTo(ConfigErrorCode.SHARED_INVALID);
        assertThat(error(()->privateCode(7404,"not_registered","private"))).isEqualTo(ConfigErrorCode.SHARED_INVALID);
        assertThat(error(()->privateCode(7404,key,"bad\u0000code"))).isEqualTo(ConfigErrorCode.SHARED_INVALID);
        assertThat(error(()->privateCode(7404,key,"\uD800"))).isEqualTo(ConfigErrorCode.SHARED_INVALID);
    }
    @Test void concurrentTenantsAndPublisherSerializeOnTheHeadWithoutDuplicateOrSwappedClaims() throws Exception {
        String key="config_code_race";var before=detail(key);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(3)){
            var first=pool.submit(()->{start.await();privateCode(7406,key,"same-private");return true;});
            var second=pool.submit(()->{start.await();privateCode(7407,key,"same-private");return true;});
            var publisher=pool.submit(()->{start.await();return Users.as(OPERATOR,()->{
                var current=shared.get(before.id());
                var draft=shared.saveDictionary(before.id(),current.draftVersion(),new DictionaryContent(1,label("Race"),
                        List.of(item("root",null),item("child","root"),item("published-race","root"))));
                shared.publish(before.id(),draft.draftVersion(),draft.publishedRevisionId());return true;
            });});
            start.countDown();
            assertThat(first.get(30,TimeUnit.SECONDS)).isTrue();assertThat(second.get(30,TimeUnit.SECONDS)).isTrue();assertThat(publisher.get(30,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(rows(before.id())).isEqualTo(4);assertThat(head(key).getDictionaryCodeCount()).isEqualTo(4);
        assertThat(error(()->privateCode(7408,key,"published-race"))).isEqualTo(ConfigErrorCode.DICTIONARY_CODE_CONFLICT);
    }
    @Test void capacityRejectsOnlyNewNamesAndKeepsExistingCodesUsable() {
        String key="config_code_capacity";long head=head(key).getId();
        privateCode(7409,key,"existing");
        jdbc.update("UPDATE config_shared_head SET dictionary_code_count=? WHERE id=?",DictionaryCodeRegistry.MAX_CODES,head);
        assertThat(error(()->privateCode(7410,key,"new-name"))).isEqualTo(ConfigErrorCode.DICTIONARY_CODE_CAPACITY);
        privateCode(7410,key,"existing");
        assertThat(rows(head)).isEqualTo(3);assertThat(head(key).getDictionaryCodeCount()).isEqualTo(DictionaryCodeRegistry.MAX_CODES);
    }
    @Test void startupBackfillsAlreadyPublishedCodesFromBeforeTheRegistryAndIsIdempotent() {
        String key="config_code_backfill";long head=head(key).getId();
        jdbc.update("DELETE FROM config_dictionary_code WHERE head_id=?",head);
        jdbc.update("UPDATE config_shared_head SET dictionary_code_count=0 WHERE id=?",head);
        host(()->new TransactionTemplate(transactions).execute(status->{shared.initialize(Kind.DICTIONARY,key);return null;}));
        host(()->new TransactionTemplate(transactions).execute(status->{shared.initialize(Kind.DICTIONARY,key);return null;}));
        assertThat(rows(head)).isEqualTo(2);assertThat(head(key).getDictionaryCodeCount()).isEqualTo(2);
        assertThat(error(()->privateCode(7411,key,"root"))).isEqualTo(ConfigErrorCode.DICTIONARY_CODE_CONFLICT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='config_dictionary_code' AND column_name IN ('tenant_id','code','label_json','parent_code')",Long.class)).isZero();
    }
}
