package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.*;
import com.sma.admin.config.internal.service.ConfigurationCatalogRegistry.Kind;
import com.sma.admin.config.internal.service.DefinedDictionaryService.*;
import com.sma.admin.config.internal.service.SharedConfigurationService.DictionaryContent;
import com.sma.admin.support.*;

@IntegrationTest @Import(DefinedDictionaryTests.CatalogConfiguration.class)
@TestPropertySource(properties="sma.tenant.platform-admin-user-ids=991")
class DefinedDictionaryTests {
    @Autowired DefinedDictionaryService service;
    @Autowired SharedConfigurationService shared;
    @Autowired DictionaryTypeMapper legacyTypes;
    @Autowired DictionaryItemMapper legacyItems;
    @Autowired ConfigNames names;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    private static final Set<String> EDIT=Set.of("config:dictionary:update");
    private static final SmaPrincipal OPERATOR=new SmaPrincipal(991,"dictionary-layer-operator",1,"admin",null,false,DataAccess.unrestricted(),
            Set.of("config:shared:view","config:shared:edit","config:shared:publish"),"UTC");
    private static ConfigurationLabel label(String text){return new ConfigurationLabel(text,null,Map.of());}
    private static DictionaryDefinition.Item item(String code,String parent){return new DictionaryDefinition.Item(code,parent,label("Shared "+code),true,0);}
    private static DictionaryDefinition definition(String key,DictionaryDefinition.Mode mode,boolean tree,List<DictionaryDefinition.Item> items){
        return new DictionaryDefinition("config",key,label(key),mode,tree,items);
    }
    record Catalog(List<DictionaryDefinition> dictionaries) implements ConfigurationCatalog {}
    @TestConfiguration(proxyBeanMethods=false) static class CatalogConfiguration {
        @Bean ConfigurationCatalog dictionaryLayerCatalog(){
            var tree=List.of(item("root",null),item("child","root"),item("leaf","child"));
            return new Catalog(List.of(
                definition("config_layer_tree",DictionaryDefinition.Mode.EXTENDABLE,true,tree),
                definition("config_layer_private_codes",DictionaryDefinition.Mode.EXTENDABLE,true,tree),
                definition("config_layer_publication",DictionaryDefinition.Mode.EXTENDABLE,true,tree),
                definition("config_layer_legacy",DictionaryDefinition.Mode.EXTENDABLE,true,List.of(item("root",null),item("child","root"),item("spare","root"))),
                definition("config_layer_conflict",DictionaryDefinition.Mode.EXTENDABLE,true,List.of(item("root",null),item("child","root"))),
                definition("config_layer_locked",DictionaryDefinition.Mode.PLATFORM_LOCKED,false,List.of(item("a",null),item("b",null))),
                definition("config_layer_private",DictionaryDefinition.Mode.TENANT_ONLY,true,List.of())));
        }
    }
    @BeforeEach void english(){LocaleContextHolder.setLocale(Locale.US);}
    @AfterEach void reset(){LocaleContextHolder.resetLocaleContext();}
    private <T>T as(long tenant,Supplier<T> work){return Users.as(new SmaPrincipal(991,"dictionary-layer-editor",tenant,"admin",null,false,DataAccess.unrestricted(),EDIT,"UTC"),work);}
    private <T>T host(long tenant,Supplier<T> work){
        try(var pool=Executors.newSingleThreadExecutor()){return pool.submit(()->TenantScope.within(tenant,work)).get(30,TimeUnit.SECONDS);}
        catch(ExecutionException failure){if(failure.getCause() instanceof RuntimeException cause)throw cause;throw new IllegalStateException(failure);}
        catch(Exception failure){throw new IllegalStateException(failure);}
    }
    private ErrorCode error(Runnable work){try{work.run();}catch(BizException failure){return failure.getErrorCode();}throw new AssertionError("Expected business rejection");}
    private static List<String> codes(List<DictionaryOption> options){
        var result=new ArrayList<String>();for(var option:options){result.add(option.value());result.addAll(codes(option.children()));}return result;
    }
    private long rows(String sql,Object... args){return jdbc.queryForObject(sql,Long.class,args);}

    @Test void newTenantsInheritAndOverridesHideOrRestoreWithoutChangingOtherTenants() {
        String key="config_layer_tree";
        as(7501,()->{
            var initial=service.get(key);assertThat(initial.layerId()).isNull();assertThat(codes(initial.snapshot().options())).containsExactly("root","child","leaf");
            assertThat(initial.snapshot().find("child").orElseThrow().origin()).isEqualTo(ConfigurationOrigin.PLATFORM);
            var renamed=service.overrideItem(key,initial.snapshot().revision(),"child",new ItemChange(new ConfigurationLabel("Tenant child",null,Map.of("en-US","Tenant child EN")),5,null));
            assertThat(renamed.snapshot().find("child").orElseThrow().label()).isEqualTo("Tenant child EN");
            assertThat(renamed.snapshot().find("child").orElseThrow().origin()).isEqualTo(ConfigurationOrigin.TENANT_OVERRIDE);
            var hidden=service.overrideItem(key,renamed.snapshot().revision(),"root",new ItemChange(null,null,true));
            assertThat(hidden.snapshot().options()).isEmpty();
            assertThat(hidden.snapshot().find("leaf").orElseThrow().label()).isEqualTo("Shared leaf");assertThat(hidden.snapshot().accepts("leaf")).isFalse();
            assertThat(error(()->service.overrideItem(key,initial.snapshot().revision(),"root",new ItemChange(null,null,false)))).isEqualTo(ConfigErrorCode.SHARED_STALE);
            assertThat(error(()->service.overrideItem(key,hidden.snapshot().revision(),"root",new ItemChange(null,null,null)))).isEqualTo(ConfigErrorCode.SHARED_INVALID);
            assertThat(error(()->service.overrideItem(key,hidden.snapshot().revision(),"missing",new ItemChange(null,null,true)))).isEqualTo(CommonErrorCode.DATA_NOT_FOUND);
            var restored=service.restoreItem(key,hidden.snapshot().revision(),"root");
            assertThat(codes(restored.snapshot().options())).containsExactly("root","child","leaf");
            assertThat(restored.snapshot().revision().tenant()).isGreaterThan(hidden.snapshot().revision().tenant());
            assertThat(restored.overriddenCodes()).containsExactly("child");return null;
        });
        as(7502,()->{var other=service.get(key);assertThat(other.layerId()).isNull();assertThat(other.snapshot().find("child").orElseThrow().label()).isEqualTo("Shared child");return null;});
    }
    @Test void privateCodesKeepHistoryAndRejectedWritesRollBackNamespaceClaims() {
        String key="config_layer_private_codes";
        as(7503,()->{
            var start=service.get(key);
            var added=service.savePrivate(key,start.snapshot().revision(),new PrivateItem("mine","root",label("Mine"),1,true));
            assertThat(codes(added.snapshot().options())).contains("mine");assertThat(added.snapshot().find("mine").orElseThrow().origin()).isEqualTo(ConfigurationOrigin.TENANT_ONLY);
            assertThat(error(()->service.savePrivate(key,added.snapshot().revision(),new PrivateItem("child",null,label("Taken"),0,true)))).isEqualTo(ConfigErrorCode.DICTIONARY_CODE_CONFLICT);
            long claims=rows("SELECT COUNT(*) FROM config_dictionary_code c JOIN config_shared_head h ON h.id=c.head_id WHERE h.config_key=?",key);
            assertThat(error(()->service.savePrivate(key,added.snapshot().revision(),new PrivateItem("orphan","absent",label("Orphan"),0,true)))).isEqualTo(ConfigErrorCode.SHARED_INVALID);
            assertThat(rows("SELECT COUNT(*) FROM config_dictionary_code c JOIN config_shared_head h ON h.id=c.head_id WHERE h.config_key=?",key)).isEqualTo(claims);
            assertThat(service.get(key).snapshot().revision()).isEqualTo(added.snapshot().revision());
            var disabled=service.savePrivate(key,added.snapshot().revision(),new PrivateItem("mine","root",label("Mine renamed"),1,false));
            assertThat(disabled.snapshot().find("mine").orElseThrow().label()).isEqualTo("Mine renamed");assertThat(disabled.snapshot().accepts("mine")).isFalse();
            assertThat(codes(disabled.snapshot().options())).doesNotContain("mine");return null;
        });
        as(7504,()->{var current=service.get(key);assertThat(service.savePrivate(key,current.snapshot().revision(),new PrivateItem("mine",null,label("Other mine"),0,true)).snapshot().accepts("mine")).isTrue();return null;});
        Users.as(new SmaPrincipal(991,"dictionary-reader",7503,"admin",null,false,DataAccess.unrestricted(),Set.of("config:dictionary:view"),"UTC"),()->{
            var current=service.get(key);
            assertThatThrownBy(()->service.savePrivate(key,current.snapshot().revision(),new PrivateItem("denied",null,label("Denied"),0,true))).isInstanceOf(AccessDeniedException.class);return null;
        });
    }
    @Test void lockedAndTenantOnlyModesKeepTheirOwnership() {
        as(7505,()->{
            var locked=service.get("config_layer_locked");
            assertThat(error(()->service.overrideItem("config_layer_locked",locked.snapshot().revision(),"a",new ItemChange(label("No"),null,null)))).isEqualTo(ConfigErrorCode.SHARED_INVALID);
            assertThat(error(()->service.savePrivate("config_layer_locked",locked.snapshot().revision(),new PrivateItem("c",null,label("No"),0,true)))).isEqualTo(ConfigErrorCode.SHARED_INVALID);
            assertThat(error(()->service.enabled("config_layer_locked",locked.snapshot().revision(),false))).isEqualTo(ConfigErrorCode.SHARED_INVALID);
            var own=service.get("config_layer_private");assertThat(own.snapshot().revision().platform()).isZero();assertThat(own.snapshot().options()).isEmpty();
            var parent=service.savePrivate("config_layer_private",own.snapshot().revision(),new PrivateItem("top",null,label("Top"),0,true));
            var child=service.savePrivate("config_layer_private",parent.snapshot().revision(),new PrivateItem("low","top",label("Low"),0,true));
            assertThat(codes(child.snapshot().options())).containsExactly("top","low");
            var off=service.enabled("config_layer_private",child.snapshot().revision(),false);
            assertThat(off.enabled()).isFalse();assertThat(off.snapshot().options()).isEmpty();assertThat(off.snapshot().find("low").orElseThrow().label()).isEqualTo("Low");return null;
        });
        assertThat(rows("SELECT COUNT(*) FROM config_shared_head WHERE config_key='config_layer_private'")).isZero();
    }
    @Test void platformDisabledCodesCannotBeReenabledAndPublicationsInvalidateOldVersions() {
        String key="config_layer_publication";
        var before=as(7506,()->{var current=service.get(key);return service.overrideItem(key,current.snapshot().revision(),"child",new ItemChange(null,null,false));});
        Users.as(OPERATOR,()->{
            var head=shared.page(new PageQuery(1,20,null,null),Kind.DICTIONARY,key).items().getFirst();
            var draft=shared.saveDictionary(head.id(),head.draftVersion(),new DictionaryContent(1,label(key),List.of(item("root",null),
                    new DictionaryDefinition.Item("child","root",label("Shared child"),false,0),item("leaf","child"))));
            shared.publish(head.id(),draft.draftVersion(),draft.publishedRevisionId());return null;
        });
        as(7506,()->{
            var current=service.get(key);assertThat(current.snapshot().accepts("child")).isFalse();assertThat(current.snapshot().accepts("leaf")).isFalse();
            assertThat(current.snapshot().find("child").orElseThrow().label()).isEqualTo("Shared child");
            assertThat(error(()->service.restoreItem(key,before.snapshot().revision(),"child"))).isEqualTo(ConfigErrorCode.SHARED_STALE);return null;
        });
    }
    @Test void legacyTreesKeepIdsPresentationAndRemovedOptionsAndConflictsLeaveNoPartialState() {
        long tenant=7507;String key="config_layer_legacy";
        var ids=as(tenant,()->{
            var type=new DictionaryTypeEntity();type.setTypeKey(key);type.setName("Legacy type");type.setTree(true);type.setEnabled(true);type.setBuiltin(false);type.setSort(0);legacyTypes.insert(type);
            long root=legacy(type.getId(),0,"root","Legacy root",9,true);names.replace("dictionary-item",root,Map.of("en-US","Legacy root EN"));
            long child=legacy(type.getId(),root,"child","Legacy child",1,false);
            long old=legacy(type.getId(),root,"old","Old private",2,true);long oldChild=legacy(type.getId(),old,"old-child","Old child",0,true);
            return List.of(type.getId(),root,child,old,oldChild);
        });
        assertThatThrownBy(()->as(tenant,service::migrateCurrentTenant)).isInstanceOf(AccessDeniedException.class);
        assertThat(host(tenant,service::migrateCurrentTenant)).isEqualTo(1);
        as(tenant,()->{
            var state=service.get(key);var snapshot=state.snapshot();
            assertThat(snapshot.find("root").orElseThrow().label()).isEqualTo("Legacy root EN");assertThat(snapshot.find("root").orElseThrow().sort()).isEqualTo(9);
            assertThat(snapshot.accepts("child")).isFalse();assertThat(snapshot.accepts("spare")).isFalse();
            assertThat(codes(snapshot.options())).containsExactly("root","old","old-child");
            assertThat(state.privateCodes()).containsExactlyInAnyOrder("old","old-child");assertThat(state.overriddenCodes()).containsExactlyInAnyOrder("root","child","spare");
            assertThat(state.label().text()).isEqualTo("Legacy type");return null;
        });
        assertThat(rows("SELECT COUNT(*) FROM config_dictionary_entry WHERE tenant_id=? AND legacy_item_id IN (?,?,?,?)",tenant,ids.get(1),ids.get(2),ids.get(3),ids.get(4))).isEqualTo(4);
        assertThat(rows("SELECT COUNT(*) FROM config_dictionary_item WHERE tenant_id=? AND deleted=0",tenant)).isEqualTo(4);
        assertThat(host(tenant,service::migrateCurrentTenant)).isZero();
        long conflict=7508;
        as(conflict,()->{
            var type=new DictionaryTypeEntity();type.setTypeKey("config_layer_conflict");type.setName("Conflict");type.setTree(true);type.setEnabled(true);type.setBuiltin(false);type.setSort(0);legacyTypes.insert(type);
            // The private claim is attempted before the conflicting shared row and must roll back with it.
            legacy(type.getId(),0,"private-before-failure","Private",0,true);legacy(type.getId(),0,"child","Secret label must not leak",0,true);return null;
        });
        String claims="SELECT COUNT(*) FROM config_dictionary_code c JOIN config_shared_head h ON h.id=c.head_id WHERE h.config_key='config_layer_conflict'";
        long before=rows(claims);
        assertThatThrownBy(()->host(conflict,service::migrateCurrentTenant)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant=7508").hasMessageContaining("config_layer_conflict").hasMessageNotContaining("Secret label").hasNoCause();
        assertThat(rows("SELECT COUNT(*) FROM config_dictionary_layer WHERE tenant_id=?",conflict)).isZero();
        assertThat(rows("SELECT COUNT(*) FROM config_write_lock WHERE tenant_id=? AND dictionary_revision>0",conflict)).isZero();
        assertThat(rows(claims)).isEqualTo(before);
    }
    @Test void callerRollbackRestoresVersionAndRows() {
        String key="config_layer_tree";
        as(7509,()->{
            var before=service.get(key);
            assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{
                service.overrideItem(key,before.snapshot().revision(),"leaf",new ItemChange(label("Rolled back"),null,null));throw new IllegalStateException("fixture rollback");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(service.get(key).snapshot()).isEqualTo(before.snapshot());return null;
        });
        assertThat(rows("SELECT COUNT(*) FROM config_dictionary_layer WHERE tenant_id=7509")).isZero();
    }
    private long legacy(long type,long parent,String code,String name,int sort,boolean enabled){
        var row=new DictionaryItemEntity();row.setTypeId(type);row.setParentId(parent);row.setItemValue(code);row.setName(name);row.setSort(sort);row.setEnabled(enabled);row.setBuiltin(false);
        legacyItems.insert(row);return row.getId();
    }
}
