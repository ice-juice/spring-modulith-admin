package com.sma.business.capabilitydemo.internal.service;

import java.util.*;
import com.sma.admin.common.security.ActorRef;
import com.sma.admin.file.*;
import org.springframework.stereotype.Component;

@Component
final class AttachmentScenes implements FileSceneCatalog,FileScenePolicy {
    private final AttachmentAuthorization authorization;
    AttachmentScenes(AttachmentAuthorization authorization){this.authorization=authorization;}
    @Override public String application(){return "capabilitydemo";}
    @Override public Collection<Definition> scenes(){return List.of(new Definition(application(),"capabilitydemo.document",
            "capabilitydemo.scene.document",1048576,Set.of(FileFormat.TEXT),Set.of(UploadMode.SERVER)));}
    @Override public boolean canUse(ActorRef actor,FileSceneAccess access){
        return access.application().equals(application()) && access.scene().equals("capabilitydemo.document")
                && access.action()==FileSceneAccess.Action.UPLOAD && "document".equals(access.objectType())
                && access.objectId()!=null && authorization.mayUpload(actor,access.objectId());
    }
}
