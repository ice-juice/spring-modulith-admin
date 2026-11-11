package com.sma.admin.iam;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.support.*;

@IntegrationTest
class PermissionIconTests {
 @Autowired PermissionService resources;
 @Autowired RoleService roles;
 @Autowired RolePermissionService grants;
 @Autowired PermissionQuery query;
 private PermissionService.Input input(long parent,String key,String type,String icon,boolean enabled,boolean defaultGrant) {
  return new PermissionService.Input(parent,key,key,null,type,"MENU".equals(type)?"/icons":null,icon,"ROUTE",0,enabled,false,defaultGrant,Map.of());
 }
 @Test void metadataUsesTheSameTenantRoleAndEnabledAncestorBoundaryAsPermissionKeys(){
  Users.as(new SmaPrincipal(101L,"icon-admin",481L,"admin",10L,true,DataAccess.unrestricted(),Set.of(),"UTC"),()->{
   var role=roles.save(null,new RoleService.Input(0L,"Icons","icons",0,true,DataScope.ALL,Set.of(),null));
   var menu=resources.save(null,input(0,"test:icons:view","MENU","3d:folder",true,true));
   var action=resources.save(null,input(menu.id(),"test:icons:create","BUTTON","lucide:plus",true,true));
   var other=resources.save(null,input(0,"test:private:view","MENU","3d:lock",true,false));
   grants.replace(role.id(),Set.of(action.id()));
   assertThat(query.icons(Set.of(role.id()),false)).containsExactlyInAnyOrderEntriesOf(Map.of(menu.permissionKey(),"3d:folder",action.permissionKey(),"lucide:plus"));
   assertThat(query.icons(Set.of(),false)).isEmpty();
   assertThat(query.icons(Set.of(),true)).doesNotContainKey(other.permissionKey());
   Users.as(Users.principal(101,482,DataAccess.unrestricted()),()->{assertThat(query.icons(Set.of(role.id()),true)).isEmpty();return null;});
   resources.save(menu.id(),input(0,menu.permissionKey(),"MENU","3d:folder",false,true));
   assertThat(query.icons(Set.of(role.id()),false)).isEmpty();
   assertThat(query.icons(Set.of(),true)).isEmpty();
   return null;
  });
 }
}
