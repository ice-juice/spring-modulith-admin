package com.sma.admin.file.internal.service;

import java.io.InputStream;
import java.util.List;
import com.sma.admin.common.BizException;
import com.sma.admin.file.*;
import org.springframework.stereotype.Service;

@Service
public class DefaultFileUploadApi implements FileUploadApi {
    private final UploadTaskStore tasks;
    private final LocalUploadCoordinator local;
    public DefaultFileUploadApi(UploadTaskStore tasks,LocalUploadCoordinator local){this.tasks=tasks;this.local=local;}
    @Override public Task initiate(Command command){
        if(command==null)throw new BizException(FileErrorCode.INVALID_UPLOAD);
        FileSceneAccess access;
        try{access=new FileSceneAccess(command.application(),command.scene(),FileSceneAccess.Action.UPLOAD,command.objectType(),command.objectId(),null);}
        catch(IllegalArgumentException invalid){throw new BizException(FileErrorCode.INVALID_UPLOAD);}
        return view(tasks.initiate(new UploadTaskStore.Input(command.key(),access,command.preferredProfile(),command.mode(),command.name(),command.format(),command.size(),command.checksum())));
    }
    @Override public Task status(String id){return view(tasks.status(id));}
    @Override public List<Part> parts(String id,int after,int limit){return tasks.parts(id,after,limit).stream().map(p->new Part(p.number(),p.size(),p.checksum(),p.state())).toList();}
    @Override public Task putPart(String id,int number,long size,String checksum,InputStream input){return view(local.part(id,number,size,checksum,input));}
    @Override public Task complete(String id,long version){return view(local.complete(id,version));}
    @Override public Task abort(String id,long version){return view(tasks.abort(id,version));}
    private Task view(UploadTaskStore.View v){return new Task(v.uploadId(),v.fileId(),v.state(),v.version(),v.name(),v.format(),v.mode(),v.expectedSize(),v.partBytes(),v.partCount(),v.receivedParts(),v.receivedBytes(),v.expiresAt());}
}
