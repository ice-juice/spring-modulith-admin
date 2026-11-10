package com.sma.admin.iam.internal.service;

import java.util.List;
import java.util.ArrayList;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.*;

@Component
public class IamPermissionCatalog implements PermissionCatalog {
    @Override
    public List<PermissionSpec> permissions() {
        var specs=new ArrayList<PermissionSpec>();
        specs.add(new PermissionSpec("iam:organization",null,"iam.menu.organization","组织与权限","DIRECTORY",null,"Setting",10,"admin",true));
        String[] resources={"department","post","role","permission","user","navigation"};
        String[] names={"部门管理","岗位管理","角色管理","权限资源","用户管理","顶部导航"};
        String[] paths={"/iam/departments","/iam/posts","/iam/roles","/iam/permissions","/iam/users","/iam/navigation"};
        for(int i=0;i<resources.length;i++) {
            String resource=resources[i];
            String key="iam:"+resource+":view";
            specs.add(new PermissionSpec(key,"iam:organization","iam.menu."+resource,names[i],"MENU",paths[i],null,i*10,"admin",true));
            for(String action:List.of("create","update","delete")) {
                specs.add(new PermissionSpec("iam:"+resource+":"+action,key,"iam.action."+action,action,"BUTTON",null,null,10,"admin",true));
            }
        }
        specs.add(new PermissionSpec("iam:role:grant","iam:role:view","iam.action.grant","角色授权","BUTTON",null,null,20,"admin",true));
        specs.add(new PermissionSpec("iam:user:reset-password","iam:user:view","iam.action.reset-password","重置密码","BUTTON",null,null,20,"admin",true));
        return specs;
    }
}
