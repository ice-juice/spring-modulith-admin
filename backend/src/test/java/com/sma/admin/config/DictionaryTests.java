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
 @Test void builtinCatalogIsCompleteIdempotentAndProtected() {
  assertThat(service.types()).hasSize(13);
  builtin.initialize();
  assertThat(service.types()).hasSize(13);
  var type=service.types().stream().filter(row -> row.typeKey().equals("sex")).findFirst().orElseThrow();
  assertThatThrownBy(() -> service.deleteType(type.id())).isInstanceOf(BizException.class);
  try { LocaleContextHolder.setLocale(Locale.US); assertThat(service.options("sex")).extracting(DictionaryOption::label).containsExactly("Male","Female"); }
  finally { LocaleContextHolder.resetLocaleContext(); }
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
