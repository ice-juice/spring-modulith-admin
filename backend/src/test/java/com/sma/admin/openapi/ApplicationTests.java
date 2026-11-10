package com.sma.admin.openapi;
import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.openapi.internal.persistence.ApplicationMapper;
import com.sma.admin.openapi.internal.service.*;
import com.sma.admin.support.*;
@IntegrationTest @AutoConfigureMockMvc
class ApplicationTests {
 @Autowired ApplicationService applications;@Autowired ApplicationMapper mapper;@Autowired SecretCipher cipher;@Autowired MockMvcTester mvc;
 private ApplicationService.Input input(String name){return new ApplicationService.Input(name,1,"Example",true,true);}
 @Test void credentialsAreEncryptedAndBoundToAppIdAndOnlyReturnedAtCreationAndRotation(){
  var actor=new SmaPrincipal(101,"app-admin",281,"admin",10L,false,DataAccess.unrestricted(),Set.of("openapi:app:view"),"UTC");
  Users.as(actor,()->{
   var created=applications.create(input("Integration"));assertThat(created.appId()).hasSize(32);assertThat(created.appSecret()).hasSize(43);
   var stored=mapper.selectById(created.id());assertThat(stored.getSecretCipher()).doesNotContain(created.appSecret());assertThat(cipher.decrypt(stored.getSecretCipher(),created.appId())).isEqualTo(created.appSecret());
   assertThatThrownBy(()->cipher.decrypt(stored.getSecretCipher(),"another-app")).isInstanceOf(BizException.class);
   Users.as(actor,()->{assertThat(mvc.get().uri("/api/openapi/apps/"+created.id())).hasStatusOk().bodyJson().doesNotHavePath("$.data.appSecret").doesNotHavePath("$.data.secretCipher");return null;});
   var rotated=applications.rotate(created.id());assertThat(rotated.appSecret()).isNotEqualTo(created.appSecret());assertThat(mapper.selectById(created.id()).getCredentialVersion()).isEqualTo(1L);
   assertThat(applications.types()).hasSize(13);return null;
  });
 }
 @Test void tenantPortalDataScopeUniqueNamesAndTargetDeleteProtectionAreEnforced(){
  Users.as(Users.principal(101,282,DataAccess.unrestricted()),()->{
   var row=applications.create(input("Scoped"));assertThatThrownBy(()->applications.create(input("Scoped"))).isInstanceOf(BizException.class);
   Users.as(Users.principal(202,282,DataAccess.own()),()->{assertThatThrownBy(()->applications.get(row.id())).isInstanceOf(BizException.class);return null;});
   Users.as(Users.principal(101,283,DataAccess.unrestricted()),()->{assertThatThrownBy(()->applications.get(row.id())).isInstanceOf(BizException.class);return null;});
   Users.as(new SmaPrincipal(101,"app-admin",282,"other",null,false,DataAccess.unrestricted(),Set.of(),"UTC"),()->{assertThatThrownBy(()->applications.get(row.id())).isInstanceOf(BizException.class);return null;});
   applications.link(row.id(),new ApplicationService.Target("external","9007199254740993","External integration"));assertThatThrownBy(()->applications.delete(row.id())).isInstanceOf(BizException.class);
   applications.unlink(row.id());assertThat(applications.get(row.id()).targetId()).isNull();applications.delete(row.id());applications.create(input("Scoped"));return null;
  });
 }
}
