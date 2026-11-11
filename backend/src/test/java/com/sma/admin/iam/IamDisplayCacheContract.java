package com.sma.admin.iam;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.admin.common.OptionItem;
import com.sma.admin.common.cache.ManagedCache;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.*;
import com.sma.admin.support.Users;

abstract class IamDisplayCacheContract {
    @Autowired DepartmentService departments;
    @Autowired PostService posts;
    @Autowired DirectoryService directory;
    @Autowired UserService users;
    @Autowired ProfileService profiles;
    @Autowired PermissionService permissions;
    @Autowired NavigationService navigation;
    @Autowired RoleService roles;
    @Autowired RolePermissionService grants;
    @Autowired ManagedCache cache;
    @Autowired PlatformTransactionManager transactions;
    abstract long tenantBase();

    @Test void departmentCacheKeysFollowFreshDataScopeAndRollbackPreservesCommittedDisplay() {
        long tenant=tenantBase();
        Users.as(actor(101,tenant,"admin"),()->{
            var a=departments.save(null,department("Alpha","alpha"));
            var b=departments.save(null,department("Beta","beta"));
            assertThat(departments.list(null)).hasSize(2);
            assertThat(departments.list(null)).hasSize(2); hit(IamDisplayCaches.DEPARTMENTS);
            Users.as(Users.principal(102,tenant,new DataAccess(false,Set.of(a.id()),false)),()->{
                assertThat(departments.list(null)).extracting(DepartmentService.View::id).containsExactly(a.id());
                assertThat(departments.list(null)).extracting(DepartmentService.View::id).containsExactly(a.id()); return null;
            });
            Users.as(Users.principal(102,tenant,new DataAccess(false,Set.of(b.id()),false)),()->{
                assertThat(departments.list(null)).extracting(DepartmentService.View::id).containsExactly(b.id()); return null;
            });
            Users.as(Users.principal(102,tenant,DataAccess.own()),()->{assertThat(departments.list(null)).isEmpty();return null;});
            new TransactionTemplate(transactions).execute(status->{
                departments.save(a.id(),department("Uncommitted","alpha"));
                assertThat(departments.list("Uncommitted")).hasSize(1);status.setRollbackOnly();return null;
            });
            assertThat(departments.list(null)).extracting(DepartmentService.View::name).containsExactly("Alpha","Beta");
            departments.save(a.id(),department("Updated","alpha"));
            assertThat(departments.list("Updated")).hasSize(1);
            departments.delete(a.id());assertThat(departments.list(null)).extracting(DepartmentService.View::id).containsExactly(b.id());
            Users.as(actor(101,tenant+1,"admin"),()->{assertThat(departments.list(null)).isEmpty();return null;});
            return null;
        });
    }

    @Test void postOptionsReflectDisableDeleteRecreateAndRollback() {
        Users.as(actor(101,tenantBase()+2,"admin"),()->{
            assertThat(posts.options()).isEmpty();
            var post=posts.save(null,new PostService.Input("Engineer","engineer",0,true,null));
            assertThat(posts.options()).extracting(OptionItem::value).containsExactly(post.id());
            posts.options();hit(IamDisplayCaches.POSTS);
            new TransactionTemplate(transactions).execute(status->{
                posts.delete(post.id());assertThat(posts.options()).isEmpty();status.setRollbackOnly();return null;
            });
            assertThat(posts.options()).hasSize(1);
            posts.save(post.id(),new PostService.Input("Engineer","engineer",0,false,null));assertThat(posts.options()).isEmpty();
            posts.delete(post.id());
            var recreated=posts.save(null,new PostService.Input("New Engineer","engineer",0,true,null));
            assertThat(posts.options()).extracting(OptionItem::value).containsExactly(recreated.id());return null;
        });
    }

    @Test void directoryTracksCreateProfileAdminUpdateDisableDeleteBootstrapAndPortal() {
        long tenant=tenantBase()+3;
        Users.as(actor(101,tenant,"admin"),()->{
            assertThat(directory.search(null)).isEmpty();
            var user=users.create(new UserService.Create(user("cacheuser","Original",true),"Q722ee214d"));
            var ids=Set.of(user.id());
            assertThat(directory.names(ids)).containsEntry(user.id(),"Original");directory.names(ids);hit(IamDisplayCaches.USERS);
            Users.as(actor(101,tenant,"other"),()->{assertThat(directory.names(ids)).isEmpty();assertThat(directory.search(null)).isEmpty();return null;});
            Users.as(actor(101,tenant+1,"admin"),()->{assertThat(directory.names(ids)).isEmpty();return null;});
            Users.as(Users.principal(user.id(),tenant,DataAccess.unrestricted()),()->{
                profiles.update(new ProfileService.Input("Profile changed",null,null,"UNSPECIFIED",null));return null;
            });
            assertThat(directory.names(ids)).containsEntry(user.id(),"Profile changed");
            users.update(user.id(),user("cacheuser","Admin changed",false));
            assertThat(directory.search(null)).isEmpty(); // Disabled users still have historical display names.
            assertThat(directory.names(ids)).containsEntry(user.id(),"Admin changed");
            assertThat(users.findById(user.id()).orElseThrow().enabled()).isFalse();
            new TransactionTemplate(transactions).execute(status->{
                users.delete(user.id());assertThat(directory.names(ids)).isEmpty();status.setRollbackOnly();return null;
            });
            assertThat(directory.names(ids)).containsEntry(user.id(),"Admin changed");
            users.delete(user.id());assertThat(directory.names(ids)).isEmpty();
            assertThat(users.findById(user.id())).isEmpty();
            users.bootstrap("Q722ee214d");assertThat(directory.search(null)).hasSize(1);
            var many=new HashSet<Long>();for(long i=0;i<200;i++)many.add(Long.MAX_VALUE-i);
            assertThat(directory.names(many)).isEmpty(); // A 200-ID key exceeds the raw cache key limit before hashing.
            return null;
        });
    }

    @Test void translatedMetadataIsCachedButGrantRevocationRemainsAuthoritative() {
        long tenant=tenantBase()+5;
        Users.as(actor(101,tenant,"admin"),()->{
            try {
                var menu=permissions.save(null,permission("菜单","Menu",true));
                var nav=navigation.save(null,new NavigationService.Input("导航",null,0,true,Set.of(menu.id()),Map.of("en-US","Navigation")));
                LocaleContextHolder.setLocale(Locale.US);
                assertThat(permissions.list().getFirst().name()).isEqualTo("Menu");permissions.list();hit(IamDisplayCaches.PERMISSIONS);
                assertThat(navigation.visible(Set.of(menu.id())).getFirst().name()).isEqualTo("Navigation");navigation.list();hit(IamDisplayCaches.NAVIGATION);
                assertThat(navigation.visible(Set.of())).isEmpty();
                assertThatThrownBy(()->navigation.list().getFirst().menuIds().clear()).isInstanceOf(UnsupportedOperationException.class);
                LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
                assertThat(permissions.list().getFirst().name()).isEqualTo("菜单");
                assertThat(navigation.list().getFirst().name()).isEqualTo("导航");
                Users.as(actor(101,tenant,"other"),()->{assertThat(permissions.list()).isEmpty();assertThat(navigation.list()).isEmpty();return null;});
                var role=roles.save(null,new RoleService.Input(0L,"Reader","reader",0,true,DataScope.SELF,Set.of(),null));
                grants.replace(role.id(),Set.of(menu.id()));
                assertThat(grants.keys(Set.of(role.id()),false)).contains("iam:cached:view");
                assertThat(grants.menus(Set.of(role.id()),false)).hasSize(1);
                grants.replace(role.id(),Set.of());
                assertThat(grants.keys(Set.of(role.id()),false)).isEmpty();assertThat(grants.menus(Set.of(role.id()),false)).isEmpty();
                permissions.save(menu.id(),permission("新菜单","New menu",false));
                assertThat(permissions.list().getFirst().name()).isEqualTo("新菜单");
                assertThat(grants.keys(Set.of(),true)).isEmpty();
                new TransactionTemplate(transactions).execute(status->{
                    navigation.delete(nav.id());assertThat(navigation.list()).isEmpty();status.setRollbackOnly();return null;
                });
                assertThat(navigation.list()).hasSize(1);
                navigation.save(nav.id(),new NavigationService.Input("Changed",null,0,false,Set.of(menu.id()),Map.of()));
                assertThat(navigation.visible(Set.of(menu.id()))).isEmpty();
                navigation.delete(nav.id());permissions.delete(menu.id());assertThat(permissions.list()).isEmpty();
                return null;
            } finally { LocaleContextHolder.resetLocaleContext(); }
        });
    }

    @Test void catalogSyncInvalidatesNavigationRelationsAfterMovingAnExistingRoot() {
        Users.as(actor(101,tenantBase()+6,"admin"),()->{
            var old=new PermissionSpec("iam:cache-menu",null,"iam.cache.menu-metadata","Menu","MENU","/cache",null,0,"admin",true);
            permissions.sync(List.of(old));
            var menu=permissions.list().getFirst();
            navigation.save(null,new NavigationService.Input("Navigation",null,0,true,Set.of(menu.id()),Map.of()));
            assertThat(navigation.list().getFirst().menuIds()).containsExactly(menu.id());
            permissions.sync(List.of(new PermissionSpec("iam:cache-root",null,"iam.cache.navigation-metadata","Root","DIRECTORY",null,null,0,"admin",true),
                    new PermissionSpec(old.key(),"iam:cache-root",old.nameKey(),old.defaultName(),old.type(),old.path(),null,0,"admin",true)));
            var root=permissions.list().stream().filter(row->row.permissionKey().equals("iam:cache-root")).findFirst().orElseThrow();
            assertThat(navigation.list().getFirst().menuIds()).containsExactly(root.id());
            assertThat(permissions.tree().getFirst().children()).extracting(PermissionService.View::id).containsExactly(menu.id());return null;
        });
    }
    private void hit(String region) {
        var snapshot=cache.snapshot(region);
        if(snapshot.provider().equals("none")){assertThat(snapshot.size()).isZero();assertThat(snapshot.hits()).isZero();}
        else {assertThat(snapshot.size()).isPositive();assertThat(snapshot.hits()).isPositive();}
    }
    private SmaPrincipal actor(long id,long tenant,String portal){return new SmaPrincipal(id,"admin",tenant,portal,null,true,DataAccess.unrestricted(),Set.of(),"UTC");}
    private DepartmentService.Input department(String name,String code){return new DepartmentService.Input(0L,name,code,0,true);}
    private UserService.Input user(String username,String name,boolean enabled){return new UserService.Input(username,name,null,null,null,"UNSPECIFIED",enabled,Set.of(),Set.of(),"en-US","UTC");}
    private PermissionService.Input permission(String name,String english,boolean enabled){return new PermissionService.Input(0L,"iam:cached:view",name,null,"MENU","/cached",null,"ROUTE",0,enabled,false,true,Map.of("en-US",english));}
}
