package com.sma.admin.config;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.service.ParameterService;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
class ParameterTests {
 @Autowired ParameterService service;
 @Autowired MockMvcTester mvc;
 @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
 org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;
 @Autowired com.sma.admin.config.internal.service.BuiltinParameters builtin;
 @Autowired com.sma.admin.config.internal.service.DefinedParameterService layers;
 @Test void restoringBuiltinsResetsValuesAndPreservesCustomParameters(){
  Users.as(new SmaPrincipal(101,"parameter-editor",1,"admin",null,false,DataAccess.unrestricted(),Set.of("config:parameter:update"),"UTC"),()->{
  var system=layers.get("ui.theme");
  var changed=layers.override("ui.theme",system.snapshot().revision(),"dark");
  assertThatThrownBy(()->service.save(changed.rowId(),new ParameterService.Input("ui.theme","appearance","Theme",null,"STRING","light",false,false,0,Map.of()))).isInstanceOf(BizException.class);
  var custom=service.save(null,new ParameterService.Input("custom.resetCheck","basic","Custom",null,"STRING","keep",false,false,0,Map.of()));
  builtin.resetDefaults();assertThat(service.stringValue("ui.theme","")).isEqualTo("light");assertThat(service.stringValue("custom.resetCheck","")).isEqualTo("keep");
  assertThat(service.list(null,null)).noneMatch(row->row.paramKey().equals("system.name"));
  assertThatThrownBy(()->service.stringValue("system.name","")).isInstanceOf(BizException.class);
  assertThatThrownBy(()->service.save(null,new ParameterService.Input("system.name","basic","Name",null,"STRING","Another brand",true,false,0,Map.of()))).isInstanceOf(BizException.class);
  service.delete(custom.id());return null;});
 }
 @Test void typedValuesRefreshAfterCommitAndSecretsRemainServerSide(){
  Users.as(Users.principal(101,181,DataAccess.unrestricted()),()->{
   service.saveGroup(null,new ParameterService.GroupInput("business","业务",null,0,Map.of("en-US","Business")));
   var rate=service.save(null,input("order.taxRate","DECIMAL","0.08",false,false));
   assertThat(service.decimalValue("order.taxRate",BigDecimal.ZERO)).isEqualByComparingTo("0.08");
   service.save(rate.id(),input("order.taxRate","DECIMAL","0.10",false,false));
   assertThat(service.decimalValue("order.taxRate",BigDecimal.ZERO)).isEqualByComparingTo("0.10");
   assertThatThrownBy(()->service.save(null,input("flags.invalid","BOOLEAN","yes",false,false))).isInstanceOf(BizException.class);
   var secret=service.save(null,input("api.secret","STRING","server-only",false,true));
   assertThat(secret.value()).isNull();assertThat(service.publicValues()).doesNotContainKey("api.secret");
   service.save(secret.id(),input("api.secret","STRING",null,false,true));
   assertThat(service.stringValue("api.secret","")).isEqualTo("server-only");
   assertThatThrownBy(()->service.save(secret.id(),input("api.secret","STRING","server-only",true,true))).isInstanceOf(BizException.class);
   assertThatThrownBy(()->service.save(secret.id(),input("api.secret","STRING",null,true,false))).isInstanceOf(BizException.class);
   assertThatThrownBy(()->service.save(secret.id(),input("api.secret","STRING",null,false,false))).isInstanceOf(BizException.class);
   assertThat(service.publicValues()).doesNotContainKey("api.secret");
   assertThatThrownBy(()->service.save(null,input("auth.privatePolicy","STRING","private",true,false))).isInstanceOf(BizException.class);
   Users.as(Users.principal(102,182,DataAccess.unrestricted()),()->{assertThat(service.stringValue("order.taxRate","absent")).isEqualTo("absent");return null;});
   return null;
  });
 }
 @Test void typedLoginEntryIsAnonymousAndDoesNotExposeSecurityParameters(){
  assertThat(mvc.get().uri("/api/tenant/entry")).hasStatusOk().bodyJson()
   .hasPathSatisfying("$.data.login.captchaEnabled",value->value.assertThat().isEqualTo(true))
   .doesNotHavePath("$.data['auth.sessionTtlSeconds']");
  assertThat(service.longValue("auth.sessionTtlSeconds",0)).isEqualTo(28800);
  assertThatThrownBy(()->service.save(null,new ParameterService.Input("auth.sessionTtlSeconds","security","TTL",null,"INTEGER","60",false,false,0,Map.of()))).isInstanceOf(BizException.class);
 }
 @Test void retiredPublicParameterPathHasNoMappingOrAnonymousCompatibilityAlias(){
  assertThat(mappings.getHandlerMethods().keySet().stream().flatMap(info->info.getPatternValues().stream()))
   .doesNotContain("/api/config/parameters/public");
  assertThat(mvc.get().uri("/api/config/parameters/public")).hasStatus(401);
  Users.as(Users.principal(921,1810,DataAccess.unrestricted()),()->{
   // Existing PUT/DELETE /{id} patterns yield MVC 405 for an authenticated GET; there is no public GET mapping.
   assertThat(mvc.get().uri("/api/config/parameters/public")).hasStatus(405);return null;
  });
 }
 private ParameterService.Input input(String key,String type,String value,boolean publicly,boolean secret){return new ParameterService.Input(key,"business",key,null,type,value,publicly,secret,0,Map.of());}
}
