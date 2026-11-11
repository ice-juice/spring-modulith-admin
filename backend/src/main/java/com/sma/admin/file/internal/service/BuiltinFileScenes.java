package com.sma.admin.file.internal.service;

import java.util.*;
import com.sma.admin.common.security.*;
import com.sma.admin.file.*;
import org.springframework.stereotype.Component;

/** Existing admin attachment/image use cases. Does not authorize application users or replace resource ownership checks. */
@Component
public class BuiltinFileScenes implements FileSceneCatalog,FileScenePolicy {
    @Override public Collection<Definition> scenes(){return List.of(
            new Definition("file","file.image","file.scene.image",5L*1024*1024,Set.of(FileFormat.PNG,FileFormat.JPEG,FileFormat.GIF,FileFormat.WEBP),Set.of(UploadMode.SERVER)),
            new Definition("file","file.attachment","file.scene.attachment",20L*1024*1024,Set.of(FileFormat.PNG,FileFormat.JPEG,FileFormat.GIF,FileFormat.WEBP,FileFormat.PDF,FileFormat.TEXT,FileFormat.CSV,FileFormat.JSON,FileFormat.DOCX,FileFormat.XLSX,FileFormat.PPTX),Set.of(UploadMode.SERVER,UploadMode.MULTIPART)));}
    @Override public String application(){return "file";}
    @Override public boolean canUse(ActorRef actor,FileSceneAccess request){
        if(actor.kind()!=ActorRef.Kind.ADMIN_USER || !Set.of("file.image","file.attachment").contains(request.scene()))return false;
        var user=CurrentUser.require();
        return switch(request.action()){
            case UPLOAD->request.scene().equals("file.image") || user.permissions().contains("file:file:upload");
            case READ->true; // Runtime must still require file ownership/data grants or an existing FileAccessPolicy reference.
            case DELETE->user.permissions().contains("file:file:delete");
            case BIND,UNBIND->false; // Legacy consumers retain their explicit FileQuery/reference policies.
        };
    }
}
