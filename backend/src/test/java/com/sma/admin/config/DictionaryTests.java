package com.sma.admin.config;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.service.DictionaryService;
import com.sma.admin.support.*;

@IntegrationTest
class DictionaryTests {
 @Autowired DictionaryService service;
 @Autowired com.sma.admin.config.internal.service.BuiltinDictionaries builtin;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @Test void builtinCatalogIsSharedInheritedAndCannotBeRecreatedAsPrivateCopies() {
  Users.as(Users.principal(101,173,DataAccess.unrestricted()),() -> {
   builtin.initialize();builtin.initialize();
   assertThat(service.types()).isEmpty();
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM config_dictionary_type WHERE tenant_id=173",Long.class)).isZero();
   assertThatThrownBy(()->service.saveType(null,new DictionaryService.TypeInput("sex","Copy",null,false,true,0,Map.of()))).isInstanceOf(BizException.class);
   try { LocaleContextHolder.setLocale(Locale.US); assertThat(service.options("sex")).extracting(DictionaryOption::label).containsExactly("Male","Female");
    assertThat(service.options("extend_dic_demo").get(1).children()).extracting(DictionaryOption::value).containsExactly("v21","v22","v23"); }
   finally { LocaleContextHolder.resetLocaleContext(); }
   return null;
  });
 }
 @Test void customTreeTranslationCycleDeletionAndTenantIsolation() {
  Users.as(Users.principal(101,171,DataAccess.unrestricted()),() -> {
   var type=service.saveType(null,new DictionaryService.TypeInput("regions","地区",null,true,true,0,Map.of("en-US","Regions")));
   var parent=service.saveItem(null,new DictionaryService.ItemInput(type.id(),0L,"asia","亚洲",null,true,0,Map.of("en-US","Asia")));
   var child=service.saveItem(null,new DictionaryService.ItemInput(type.id(),parent.id(),"cn","中国",null,true,0,Map.of("en-US","China")));
   try { LocaleContextHolder.setLocale(Locale.US); assertThat(service.options("regions").getFirst().children().getFirst().label()).isEqualTo("China"); }
   finally { LocaleContextHolder.resetLocaleContext(); }
   assertThatThrownBy(() -> service.saveItem(parent.id(),new DictionaryService.ItemInput(type.id(),child.id(),"asia","亚洲",null,true,0,Map.of()))).isInstanceOf(BizException.class);
   assertThatThrownBy(() -> service.deleteType(type.id())).isInstanceOf(BizException.class);
   assertThatThrownBy(() -> service.deleteItem(parent.id())).isInstanceOf(BizException.class);
   Users.as(Users.principal(202,172,DataAccess.unrestricted()),() -> { assertThat(service.options("regions")).isEmpty(); return null; });
   service.deleteItem(child.id());service.deleteItem(parent.id());service.deleteType(type.id()); return null;
  });
 }
}
