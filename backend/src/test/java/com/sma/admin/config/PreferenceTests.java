package com.sma.admin.config;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.config.internal.service.PreferenceService;
import com.sma.admin.support.*;

@IntegrationTest
class PreferenceTests {
 @Autowired PreferenceService service;
 @Test void skinIsIndependentCompatibleAndScoped(){
  Users.as(Users.principal(201,291,DataAccess.unrestricted()),()->{
   assertThat(service.get(201,null,null).skin()).isEqualTo("iris");
   service.save(new PreferenceService.Input("zh-CN","UTC","dark",true,false,"celadon"));
   assertThat(service.get(201,null,null)).satisfies(p->{assertThat(p.skin()).isEqualTo("celadon");assertThat(p.theme()).isEqualTo("dark");});
   service.save(new PreferenceService.Input("en-US","UTC","light",true,false));
   assertThat(service.get(201,null,null).skin()).isEqualTo("celadon");
   assertThat(service.get(202,null,null).skin()).isEqualTo("iris");
   Users.as(Users.principal(201,292,DataAccess.unrestricted()),()->{assertThat(service.get(201,null,null).skin()).isEqualTo("iris");return null;});
   assertThatThrownBy(()->service.save(new PreferenceService.Input("en-US","UTC","dark",true,false,"invalid"))).isInstanceOf(BizException.class);
   service.save(new PreferenceService.Input("en-US","UTC","system",true,false,"amber"));
   assertThat(service.get(201,null,null).skin()).isEqualTo("amber");
   service.reset();assertThat(service.get(201,null,null).skin()).isEqualTo("iris");return null;
  });
 }
 @Test void preferencesAreScopedToAuthenticatedUserAndTenantAndValidateIanaZones(){
  Users.as(Users.principal(101,191,DataAccess.unrestricted()),()->{
   service.save(new PreferenceService.Input("en-US","America/New_York","dark",true,true));
   assertThat(service.get(101,"zh-CN","UTC").timeZone()).isEqualTo("America/New_York");
   assertThat(service.get(102,null,null).timeZone()).isNull();
   assertThatThrownBy(()->service.save(new PreferenceService.Input("zh-CN","Invalid/Zone","light",true,false))).isInstanceOf(BizException.class);
   Users.as(Users.principal(101,192,DataAccess.unrestricted()),()->{assertThat(service.get(101,null,null).locale()).isNull();return null;});
   service.reset();service.save(new PreferenceService.Input("zh-CN","Asia/Shanghai","light",false,false));
   assertThat(service.get(101,null,null).tabsEnabled()).isFalse();return null;
  });
 }
}
