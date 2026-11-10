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
   Users.as(Users.principal(102,182,DataAccess.unrestricted()),()->{assertThat(service.stringValue("order.taxRate","absent")).isEqualTo("absent");return null;});
   return null;
  });
 }
 @Test void publicLoginParametersAreAnonymousAndDoNotExposeSecuritySettings(){
  assertThat(mvc.get().uri("/api/config/parameters/public")).hasStatusOk().bodyJson()
   .hasPathSatisfying("$.data['system.name']",value->value.assertThat().isEqualTo("Springboot Modulith Admin"))
   .doesNotHavePath("$.data['auth.sessionTtlSeconds']");
  var builtin=service.list(null,null).stream().filter(row->row.paramKey().equals("auth.sessionTtlSeconds")).findFirst().orElseThrow();
  assertThatThrownBy(()->service.delete(builtin.id())).isInstanceOf(BizException.class);
 }
 private ParameterService.Input input(String key,String type,String value,boolean publicly,boolean secret){return new ParameterService.Input(key,"business",key,null,type,value,publicly,secret,0,Map.of());}
}
