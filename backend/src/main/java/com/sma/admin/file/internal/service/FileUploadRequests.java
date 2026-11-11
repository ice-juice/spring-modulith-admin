package com.sma.admin.file.internal.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.sma.admin.common.*;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.file.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

@Service
public class FileUploadRequests {
    private final RequestExecution requests;
    private final FileUploadStore store;
    private final FileService files;
    private final FileProperties properties;
    private final JsonMapper json;
    public FileUploadRequests(RequestExecution requests,FileUploadStore store,FileService files,FileProperties properties,JsonMapper json){
        this.requests=requests;this.store=store;this.files=files;this.properties=properties;this.json=json;
    }
    public record Result(FileUploadReceipt receipt,FileView file){}
    public Result upload(String key,MultipartFile file,boolean imageOnly){
        String scope=scope(key);store.authorize(imageOnly);
        var input=inspect(file);
        String operation=imageOnly?"file.upload-image.accept":"file.upload.accept";
        var command=new RequestCommand(operation,"file.upload",scope,key,json.writeValueAsBytes(input),imageOnly?"":"file:file:upload");
        var accepted=requests.executeLocal(command,new RequestExecution.Work<String>(){
            @Override public void authorize(){store.authorize(imageOnly);}
            @Override public String execute(){return store.accept(key,imageOnly,input.size(),input.checksum());}
        });
        if(accepted.receipt().state()!=RequestReceipt.State.SUCCEEDED)throw new RequestStateException(accepted.receipt());
        var receipt=store.byKey(key);
        if(receipt.resumable()){
            var started=store.start(receipt.uploadId());
            if(started!=null){
                var completion=new AtomicInteger(TransactionSynchronization.STATUS_UNKNOWN);
                try{
                    files.uploadAccepted(file,started.getFileId(),started.getImageOnly(),started.getExpectedSize(),started.getExpectedChecksum(),completion::set);
                }catch(RuntimeException failure){
                    // Never turn a lost commit acknowledgement into permission to write the bytes again.
                    try{
                        receipt=store.finish(receipt.uploadId(),completion.get()==TransactionSynchronization.STATUS_ROLLED_BACK);
                        if(receipt.state().equals("SUCCEEDED"))return result(receipt);
                    }catch(RuntimeException reconciliationFailure){failure.addSuppressed(reconciliationFailure);}
                    throw failure;
                }
                receipt=store.finish(receipt.uploadId(),false);
            }else receipt=store.receipt(receipt.uploadId());
        }
        return result(receipt);
    }
    public FileUploadReceipt receipt(String id){return store.receipt(id);}
    public FileUploadReceipt byKey(String key){return store.byKey(key);}
    public String scope(String key){return "user:"+CurrentUser.require().userId()+":"+FileUploadStore.keyHash(key);}
    private Result result(FileUploadReceipt receipt){
        // Current ACL and logical-deletion rules apply again, including on successful replay.
        return new Result(receipt,receipt.state().equals("SUCCEEDED")?files.metadata(receipt.fileId()):null);
    }
    record Input(String name,String type,long size,String checksum){}
    private Input inspect(MultipartFile file){
        if(file==null||file.isEmpty())throw new BizException(CommonErrorCode.BAD_REQUEST);
        if(file.getSize()>properties.maxBytes())throw new BizException(FileErrorCode.TOO_LARGE,properties.maxBytes());
        String name=Objects.toString(file.getOriginalFilename(),""),type=Objects.toString(file.getContentType(),"");
        if(name.length()>4096||type.length()>256)throw new BizException(CommonErrorCode.BAD_REQUEST);
        try(var stream=file.getInputStream()){
            var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];long size=0;int count;
            while((count=stream.read(buffer))!=-1){size+=count;if(size>properties.maxBytes())throw new BizException(FileErrorCode.TOO_LARGE,properties.maxBytes());digest.update(buffer,0,count);}
            if(size==0||size!=file.getSize())throw new BizException(CommonErrorCode.BAD_REQUEST);
            return new Input(name,type,size,HexFormat.of().formatHex(digest.digest()));
        }catch(IOException failure){throw new BizException(FileErrorCode.STORAGE);}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
