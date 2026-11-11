package com.sma.admin.file.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.file.*;
import com.sma.admin.support.Users;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class FileSceneRegistryTests {
    static class ForeignCatalog implements FileSceneCatalog {
        public Collection<Definition> scenes(){return List.of(new Definition("shop","shop.image","shop.scene.image",1000,Set.of(FileFormat.PNG),Set.of(UploadMode.SERVER)));}
    }
    static class ForeignPolicy implements FileScenePolicy {
        public String application(){return "shop";}public boolean canUse(ActorRef actor,FileSceneAccess request){return true;}
    }
    @Test void moduleOwnershipDuplicateAndMissingPoliciesFailClosed(){
        var builtin=new BuiltinFileScenes();
        assertThatThrownBy(()->new FileSceneRegistry(List.of(new ForeignCatalog()),List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new FileSceneRegistry(List.of(),List.of(new ForeignPolicy()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new FileSceneRegistry(List.of(builtin,builtin),List.of(builtin))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new FileSceneRegistry(List.of(builtin),List.of(builtin,builtin))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new FileSceneRegistry(List.of(builtin),List.of()).require("file.image")).isInstanceOf(BizException.class);
    }
    @Test void builtinScenesDoNotGiveJobsMachinesOrUnauthorisedUsersUploadPermission(){
        var builtin=new BuiltinFileScenes();var registry=new FileSceneRegistry(List.of(builtin),List.of(builtin));
        var request=new FileSceneAccess("file","file.attachment",FileSceneAccess.Action.UPLOAD,null,null,null);
        var user=new SmaPrincipal(1,"user",1,"admin",null,false,DataAccess.none(),Set.of(),"UTC");
        assertThatThrownBy(()->Users.as(user,()->{registry.authorize(request);return null;})).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->ActorContext.withinJob(1,"task",()->{registry.authorize(request);return null;})).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->Users.as(SmaPrincipal.forApplication(1,"admin","app"),()->{registry.authorize(request);return null;})).isInstanceOf(AccessDeniedException.class);
        Users.as(user,()->{registry.authorize(new FileSceneAccess("file","file.image",FileSceneAccess.Action.UPLOAD,null,null,null));return null;});
    }
}
