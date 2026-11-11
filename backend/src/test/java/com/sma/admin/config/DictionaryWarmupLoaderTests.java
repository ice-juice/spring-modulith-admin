package com.sma.admin.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import com.sma.admin.common.security.*;
import com.sma.admin.common.cache.ManagedCache;
import com.sma.admin.config.internal.persistence.*;
import com.sma.admin.config.internal.service.*;
import com.sma.admin.support.Users;

class DictionaryWarmupLoaderTests {
    private final DictionaryTypeMapper mapper=mock(DictionaryTypeMapper.class);
    private final DictionaryService dictionaries=mock(DictionaryService.class);
    private final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    private final DictionaryWarmupLoader loader=new DictionaryWarmupLoader(mapper,dictionaries,transactions,
            new ConfigurationCatalogRegistry(List.of(),tools.jackson.databind.json.JsonMapper.builder().build()));
    @Test void boundedKeysetPagesCanResumeBetweenLanguages(){
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var rows=new ArrayList<DictionaryTypeEntity>();for(long id=1;id<=9;id++){var row=new DictionaryTypeEntity();row.setId(id);row.setTypeKey("type_"+id);rows.add(row);}
        when(mapper.selectList(any())).thenReturn(rows);
        Users.as(actor(true),()->{
            var first=loader.enumerate("",3);assertThat(first.keys()).containsExactly("type_1|zh-CN","type_1|en-US","type_2|zh-CN");assertThat(first.nextCursor()).isEqualTo("2:0");assertThat(first.complete()).isFalse();
            when(mapper.selectList(any())).thenReturn(rows.subList(1,3));
            var second=loader.enumerate(first.nextCursor(),3);assertThat(second.keys()).containsExactly("type_2|en-US","type_3|zh-CN","type_3|en-US");assertThat(second.complete()).isTrue();
            assertThatThrownBy(()->loader.enumerate("arbitrary SQL",3)).isInstanceOf(IllegalArgumentException.class);return null;
        });
    }
    @Test void readsRestoreLocaleOnFailureAndRefuseTransactionOrMissingBusinessPermission(){
        LocaleContextHolder.setLocale(Locale.JAPAN);
        try{
            Users.as(actor(true),()->{
                doAnswer(call->{assertThat(LocaleContextHolder.getLocale().toLanguageTag()).isEqualTo("en-US");throw new IllegalStateException("read failed");}).when(dictionaries).warmOptions("colors");
                assertThatThrownBy(()->loader.load("colors|en-US")).isInstanceOf(IllegalStateException.class);assertThat(LocaleContextHolder.getLocale()).isEqualTo(Locale.JAPAN);
                TransactionSynchronizationManager.setActualTransactionActive(true);
                try{assertThatThrownBy(()->loader.load("colors|zh-CN")).isInstanceOf(IllegalStateException.class);}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
                assertThatThrownBy(()->loader.load("colors|arbitrary")).isInstanceOf(IllegalArgumentException.class);return null;
            });
            Users.as(actor(false),()->{assertThatThrownBy(()->loader.load("colors|zh-CN")).isInstanceOf(AccessDeniedException.class);return null;});
        }finally{LocaleContextHolder.resetLocaleContext();}
        verify(dictionaries,times(1)).warmOptions("colors");
    }
    private SmaPrincipal actor(boolean allowed){return new SmaPrincipal(101,"operator",97501,"admin",null,false,DataAccess.own(),allowed?Set.of("config:dictionary:view"):Set.of(),"UTC");}

    @Test void registeredDefinitionsWithoutTenantRowsAreEnumeratedBeforeLegacyTypes(){
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var label=new ConfigurationLabel("Shared",null,Map.of());
        var registry=new ConfigurationCatalogRegistry(List.of(new WarmCatalog(List.of(
                new DictionaryDefinition("config","config_b",label,DictionaryDefinition.Mode.EXTENDABLE,false,List.of(new DictionaryDefinition.Item("x",null,label,true,0))),
                new DictionaryDefinition("config","config_a",label,DictionaryDefinition.Mode.TENANT_ONLY,false,List.of())))),tools.jackson.databind.json.JsonMapper.builder().build());
        var sharedLoader=new DictionaryWarmupLoader(mapper,dictionaries,transactions,registry);
        var legacy=new DictionaryTypeEntity();legacy.setId(4L);legacy.setTypeKey("private_colors");
        when(mapper.selectList(any())).thenReturn(List.of(legacy));
        Users.as(actor(true),()->{
            var first=sharedLoader.enumerate("",3);assertThat(first.keys()).containsExactly("config_a|zh-CN","config_a|en-US","config_b|zh-CN");assertThat(first.nextCursor()).isEqualTo("c1:0");
            var second=sharedLoader.enumerate(first.nextCursor(),3);
            assertThat(second.keys()).containsExactly("config_b|en-US","private_colors|zh-CN","private_colors|en-US");assertThat(second.complete()).isTrue();
            assertThatThrownBy(()->sharedLoader.enumerate("c1:0;drop",3)).isInstanceOf(IllegalArgumentException.class);return null;
        });
    }
    record WarmCatalog(List<DictionaryDefinition> dictionaries) implements ConfigurationCatalog {}

    @Test void proactiveSourceReadHasAStatementDeadlineAndRefusesOversizedDictionaries(){
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"warmup-test"),DictionaryItemEntity.class);
        var items=mock(DictionaryItemMapper.class);var cache=mock(ManagedCache.class);
        var service=new DictionaryService(mapper,items,null,null,null,cache,transactions,null);
        var type=new DictionaryTypeEntity();type.setId(10L);type.setTypeKey("colors");
        when(mapper.selectOne(any())).thenReturn(type);
        when(items.selectList(any())).thenReturn(Collections.nCopies(513,new DictionaryItemEntity()));
        when(transactions.getTransaction(any())).thenAnswer(call->{
            org.springframework.transaction.TransactionDefinition definition=call.getArgument(0);
            assertThat(definition.getTimeout()).isEqualTo(3);assertThat(definition.isReadOnly()).isTrue();
            assertThat(definition.getIsolationLevel()).isEqualTo(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
            return new SimpleTransactionStatus();
        });
        doAnswer(call->call.getArgument(3,java.util.function.Supplier.class).get()).when(cache).get(eq("config.dictionary-options"),anyString(),eq(DictionaryService.OptionSnapshot.class),any());
        Users.as(actor(true),()->{assertThatThrownBy(()->service.warmOptions("colors")).isInstanceOf(IllegalStateException.class).hasMessageContaining("source budget");return null;});
        verify(items,times(1)).selectList(any());verify(transactions).rollback(any());
    }
}
