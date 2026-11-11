package com.sma.admin.file.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.request.RequestRejected;
import com.sma.admin.common.security.*;
import com.sma.admin.file.FileUploadReceipt;
import com.sma.admin.file.internal.persistence.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Short database transactions. No storage/network access, and no expiry releases execution rights. */
@Service
@Transactional(propagation=Propagation.REQUIRES_NEW, timeout=5)
public class FileUploadStore {
    private final UploadMapper uploads;
    private final FileLookupMapper files;
    private final TenantEntryQuery tenants;
    public FileUploadStore(UploadMapper uploads,FileLookupMapper files,TenantEntryQuery tenants){
        this.uploads=uploads;this.files=files;this.tenants=tenants;
    }
    @Transactional(propagation=Propagation.SUPPORTS,readOnly=true)
    public void authorize(boolean imageOnly){
        var user=CurrentUser.require();
        if(!imageOnly&&!user.permissions().contains("file:file:upload"))throw new AccessDeniedException("Upload permission required");
        generation();
    }
    private long generation(){
        long tenant=CurrentUser.tenantId();
        var state=tenants.securityState(tenant).orElseThrow(()->new AccessDeniedException("Tenant unavailable"));
        if(!state.acceptsBusiness())throw new AccessDeniedException("Tenant unavailable");
        TenantEntryScope.optional().ifPresent(entry->{
            if(entry.tenantId()!=tenant||entry.securityGeneration()!=state.generation()||!entry.portal().equals(CurrentUser.portal()))
                throw new AccessDeniedException("Tenant entry has changed");
        });
        return state.generation();
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public String accept(String key,boolean imageOnly,long size,String checksum){
        authorize(imageOnly);
        var row=new UploadEntity();row.setPublicId(UUID.randomUUID().toString());row.setKeyHash(keyHash(key));
        row.setPortal(CurrentUser.portal());row.setFileId(IdWorker.getId());row.setSecurityGeneration(generation());
        row.setImageOnly(imageOnly);row.setExpectedSize(size);row.setExpectedChecksum(checksum);row.setState("ACCEPTED");
        uploads.insert(row);return row.getPublicId();
    }
    public FileUploadReceipt byKey(String key){
        var row=uploads.selectOne(owned().eq(UploadEntity::getKeyHash,keyHash(key)).last("FOR UPDATE"));
        return reconcile(require(row));
    }
    public FileUploadReceipt receipt(String id){return reconcile(require(find(id)));}
    public UploadEntity start(String id){
        var row=require(find(id));
        if(!row.getState().equals("ACCEPTED"))return null;
        row.setState("RUNNING");uploads.updateById(row);return row;
    }
    public FileUploadReceipt finish(String id,boolean confirmedRollback){
        var row=require(find(id));
        var result=reconcile(row);
        if(!result.state().equals("SUCCEEDED")&&row.getState().equals("RUNNING")){
            row.setState(confirmedRollback?"FAILED_FINAL":"UNKNOWN");uploads.updateById(row);
            return view(uploads.selectById(row.getId()));
        }
        return result;
    }
    private UploadEntity find(String id){
        if(id==null||!id.matches("[0-9a-fA-F-]{36}"))throw new RequestRejected(RequestRejected.Reason.NOT_FOUND,0);
        return uploads.selectOne(owned().eq(UploadEntity::getPublicId,id.toLowerCase(java.util.Locale.ROOT)).last("FOR UPDATE"));
    }
    private LambdaQueryWrapper<UploadEntity> owned(){
        var user=CurrentUser.require();
        return new LambdaQueryWrapper<UploadEntity>().eq(UploadEntity::getPortal,user.portal()).eq(UploadEntity::getCreatedBy,user.userId());
    }
    private UploadEntity require(UploadEntity row){
        if(row==null)throw new RequestRejected(RequestRejected.Reason.NOT_FOUND,0);
        authorize(row.getImageOnly());
        if(row.getSecurityGeneration()!=generation())throw new AccessDeniedException("Upload belongs to a retired tenant generation");
        return row;
    }
    private FileUploadReceipt reconcile(UploadEntity row){
        if(row.getState().equals("RUNNING")||row.getState().equals("UNKNOWN")){
            var file=files.uploadEvidence(CurrentUser.tenantId(),row.getFileId(),row.getPortal(),row.getCreatedBy());
            if(file!=null&&file.getSizeBytes().equals(row.getExpectedSize())&&file.getChecksum().equals(row.getExpectedChecksum())){
                row.setState("SUCCEEDED");uploads.updateById(row);row=uploads.selectById(row.getId());
            }
        }
        return view(row);
    }
    private FileUploadReceipt view(UploadEntity row){
        String state=row.getState();
        if(state.equals("RUNNING")&&row.getUpdatedAt().plusSeconds(60).isBefore(uploads.now()))state="UNKNOWN";
        return new FileUploadReceipt(row.getPublicId(),state,state.equals("SUCCEEDED")?row.getFileId():null,
                state.equals("ACCEPTED"),row.getCreatedAt(),row.getUpdatedAt());
    }
    static String keyHash(String key){
        if(key==null||!key.matches("[A-Za-z0-9_.:-]{16,128}"))throw new RequestRejected(RequestRejected.Reason.KEY_REQUIRED,0);
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.US_ASCII)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
