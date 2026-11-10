package com.sma.admin.common.security;
import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.sma.admin.support.Users;
class MachineIdentityTests {
 @Test void applicationIdentityCarriesTenantButCannotImpersonateAHumanOrReceiveHumanGrants(){
  assertThatThrownBy(()->new SmaPrincipal(0,"user",1,"admin",null,false,DataAccess.none(),Set.of(),"UTC")).isInstanceOf(IllegalArgumentException.class);
  var machine=SmaPrincipal.forApplication(291,"partner","application");
  Users.as(machine,()->{
   assertThat(CurrentUser.tenantId()).isEqualTo(291);assertThat(CurrentUser.portal()).isEqualTo("partner");assertThat(CurrentUser.optional()).isPresent();
   assertThat(CurrentUser.optional().orElseThrow().dataAccess()).isEqualTo(DataAccess.none());assertThatThrownBy(CurrentUser::require).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);return null;
  });
  assertThatThrownBy(()->new SmaPrincipal(0,"app",1,"admin",null,true,DataAccess.unrestricted(),Set.of(),"UTC",true)).isInstanceOf(IllegalArgumentException.class);
 }
}
