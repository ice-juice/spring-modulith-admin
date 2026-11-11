package com.sma.admin.file.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.file.*;
import com.sma.admin.file.internal.persistence.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** Short persisted transitions, no file/network I/O. Tenant budget precedes task, scene, profile and part locks. */
@Service @Transactional(propagation=Propagation.REQUIRES_NEW) @EnableConfigurationProperties(UploadTaskProperties.class)
public class UploadTaskStore {
    public record Input(String key,FileSceneAccess access,Long preferredProfile,UploadMode mode,String name,FileFormat format,long size,String checksum){
        @Override public String toString(){return "UploadInput[REDACTED]";}
    }
    private record Fingerprint(FileSceneAccess access,Long preferredProfile,UploadMode mode,String name,FileFormat format,long size,String checksum){}
    private record Owner(ActorRef actor,String portal){}
    public record View(String uploadId,Long fileId,String state,Long version,String name,FileFormat format,UploadMode mode,long expectedSize,long partBytes,int partCount,
            int receivedParts,long receivedBytes,Instant expiresAt){}
    public record Part(int number,long size,String checksum,String state){}
    public record Claim(String uploadId,long taskId,long fileId,StorageSceneService.Resolved snapshot,int number,long size,String checksum,String claimId,boolean execute,String state){
        @Override public String toString(){return "UploadPartClaim[REDACTED]";}
    }
    public record FrozenPart(int number,long size,String checksum,String receipt){}
    public record Freeze(String uploadId,long taskId,StorageSceneService.Resolved snapshot,String claimId,boolean execute,long size,String checksum,List<FrozenPart> parts){
        public Freeze{parts=List.copyOf(parts);}
        @Override public String toString(){return "FreezeUpload[REDACTED]";}
    }
    private final UploadTaskMapper tasks;private final UploadPartMapper parts;private final UploadTaskBudget budgets;private final StorageSceneService scenes;
    private final UploadTaskProperties settings;private final TenantSecretCodec secrets;private final JsonMapper json;private final FileClock clock;
    public UploadTaskStore(UploadTaskMapper tasks,UploadPartMapper parts,UploadTaskBudget budgets,StorageSceneService scenes,UploadTaskProperties settings,TenantSecretCodec secrets,JsonMapper json,FileClock clock){
        this.tasks=tasks;this.parts=parts;this.budgets=budgets;this.scenes=scenes;this.settings=settings;this.secrets=secrets;this.json=json;this.clock=clock;
    }
    public View initiate(Input input){
        Fingerprint fingerprint=validate(input);Owner owner=owner();String ownerHash=hash(json.writeValueAsString(owner)),keyHash=hash(input.key());var budget=budgets.lock();
        var existing=tasks.selectOne(new LambdaQueryWrapper<UploadTaskEntity>().eq(UploadTaskEntity::getOwnerHash,ownerHash).eq(UploadTaskEntity::getKeyHash,keyHash).last("FOR UPDATE"));
        if(existing!=null){
            char[] content=json.writeValueAsString(fingerprint).toCharArray();
            try{if(!secrets.matchesFingerprint(reference(existing),"upload-intent",content,existing.getInputFingerprint()))throw new BizException(FileErrorCode.UPLOAD_CONFLICT);}
            finally{Arrays.fill(content,'\0');}
            validateLive(existing);return view(existing);
        }
        var resolved=scenes.resolveUpload(input.access(),input.preferredProfile(),input.mode());
        if(input.size()>resolved.configuration().maximumBytes() || !resolved.configuration().formats().contains(input.format()))throw invalid();
        // C4c initially implements local receiving. Cloud adapter capability admission replaces this restriction in C5-C7.
        if(resolved.profile().provider()!=StorageProvider.LOCAL || input.mode()==UploadMode.DIRECT)throw invalid();
        long partBytes=Math.min(settings.partBytes(),input.size());long count=(input.size()-1)/partBytes+1;
        if(count>settings.maximumParts())throw invalid();long reserved=Math.multiplyExact(input.size(),3);budgets.reserve(budget,reserved);
        var row=new UploadTaskEntity();row.setId(IdWorker.getId());row.setTenantId(ActorContext.tenantId());row.setPublicId(UUID.randomUUID().toString());row.setFileId(IdWorker.getId());
        row.setOwnerHash(ownerHash);row.setOwnerJson(json.writeValueAsString(owner));row.setPortal(owner.portal());row.setKeyHash(keyHash);row.setApplication(input.access().application());row.setSceneCode(input.access().scene());
        row.setSnapshotJson(json.writeValueAsString(new Snapshot(resolved,input.access())));row.setOriginalName(fingerprint.name());row.setFormat(input.format().name());row.setMode(input.mode().name());
        row.setExpectedSize(input.size());row.setExpectedChecksum(input.checksum());row.setPartBytes(partBytes);row.setPartCount((int)count);row.setState("INITIATED");row.setVersion(1L);
        row.setReceivedParts(0);row.setReceivedBytes(0L);row.setReservedBytes(reserved);row.setReservationReleased(false);
        row.setExpiresAt(clock.now().plus(settings.lifetime()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));row.setRetainUntil(clock.now().plus(settings.retention()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        char[] content=json.writeValueAsString(fingerprint).toCharArray();try{row.setInputFingerprint(secrets.fingerprint(reference(row),"upload-intent",content));}finally{Arrays.fill(content,'\0');}
        tasks.insert(row);return view(row);
    }
    public View status(String id){budgets.lock();return view(owned(id));}
    public List<Part> parts(String id,int after,int limit){
        if(after<0 || limit<1 || limit>100)throw invalid();budgets.lock();var row=owned(id);
        return parts.selectList(new LambdaQueryWrapper<UploadPartEntity>().eq(UploadPartEntity::getTaskId,row.getId()).gt(UploadPartEntity::getPartNumber,after)
                .orderByAsc(UploadPartEntity::getPartNumber).last("LIMIT "+limit)).stream().map(this::part).toList();
    }
    /** beginPart is the only permission to execute a new write. Existing RECEIVING never grants automatic retry. */
    public Claim beginPart(String id,int number,long size,String checksum){
        if(!checksum(checksum))throw invalid();budgets.lock();var task=owned(id);writable(task);
        if(number<1 || number>task.getPartCount())throw invalid();long expected=number==task.getPartCount()?task.getExpectedSize()-task.getPartBytes()*(number-1):task.getPartBytes();
        if(size!=expected)throw invalid();var row=parts.selectOne(new LambdaQueryWrapper<UploadPartEntity>().eq(UploadPartEntity::getTaskId,task.getId()).eq(UploadPartEntity::getPartNumber,number).last("FOR UPDATE"));
        if(row!=null){
            if(row.getSizeBytes()!=size || !row.getChecksum().equals(checksum))throw new BizException(FileErrorCode.UPLOAD_CONFLICT);
            if(!row.getState().equals("RETRYABLE"))return claim(task,row,false);
        }
        long receiving=parts.selectList(new LambdaQueryWrapper<UploadPartEntity>().select(UploadPartEntity::getId).eq(UploadPartEntity::getTaskId,task.getId()).eq(UploadPartEntity::getState,"RECEIVING").last("LIMIT 9 FOR UPDATE")).size();
        if(receiving>=settings.maximumConcurrentParts())throw new BizException(FileErrorCode.UPLOAD_LIMIT);
        if(row!=null){row.setState("RECEIVING");parts.updateById(row);advance(task);return claim(task,row,true);}
        row=new UploadPartEntity();row.setTaskId(task.getId());row.setPartNumber(number);row.setSizeBytes(size);row.setChecksum(checksum);row.setState("RECEIVING");row.setClaimId(UUID.randomUUID().toString());row.setAttempts(1);parts.insert(row);
        task.setState("UPLOADING");advance(task);return claim(task,row,true);
    }
    /** Called only after the internal transport measured the immutable stored bytes, including reconciliation after a lost commit. */
    public View confirmPart(Claim claim,long measuredSize,String measuredChecksum,String storageReceipt){
        if(claim==null || storageReceipt==null || !storageReceipt.matches("[A-Za-z0-9._:-]{1,512}"))throw invalid();budgets.lock();var task=owned(claim.uploadId());
        if(task.getId()!=claim.taskId() || task.getFileId()!=claim.fileId() || !Set.of("INITIATED","UPLOADING").contains(task.getState()))throw state();
        var row=parts.selectOne(new LambdaQueryWrapper<UploadPartEntity>().eq(UploadPartEntity::getTaskId,task.getId()).eq(UploadPartEntity::getPartNumber,claim.number()).last("FOR UPDATE"));
        if(row==null || !row.getClaimId().equals(claim.claimId()) || row.getSizeBytes()!=measuredSize || !row.getChecksum().equals(measuredChecksum)
                || !row.getChecksum().equals(claim.checksum()) || row.getSizeBytes()!=claim.size())throw new BizException(FileErrorCode.UPLOAD_CONFLICT);
        if(row.getState().equals("STORED")){if(!storageReceipt.equals(row.getStorageReceipt()))throw new BizException(FileErrorCode.UPLOAD_CONFLICT);return view(task);}
        if(!row.getState().equals("RECEIVING"))throw state();row.setState("STORED");row.setStorageReceipt(storageReceipt);parts.updateById(row);
        task.setReceivedParts(task.getReceivedParts()+1);task.setReceivedBytes(Math.addExact(task.getReceivedBytes(),measuredSize));advance(task);return view(task);
    }
    /** Only the local transport after locking and inspecting a closed, mismatching object may call this. Never re-arm ABSENT/BUSY. */
    public Claim retryClosedPartial(Claim previous){
        if(previous==null)throw invalid();budgets.lock();var task=owned(previous.uploadId());writable(task);
        var row=parts.selectOne(new LambdaQueryWrapper<UploadPartEntity>().eq(UploadPartEntity::getTaskId,task.getId()).eq(UploadPartEntity::getPartNumber,previous.number()).last("FOR UPDATE"));
        if(row==null || !row.getState().equals("RECEIVING") || !row.getClaimId().equals(previous.claimId()) || row.getAttempts()>=2
                || row.getSizeBytes()!=previous.size() || !row.getChecksum().equals(previous.checksum()))throw state();
        row.setClaimId(UUID.randomUUID().toString());row.setAttempts(row.getAttempts()+1);parts.updateById(row);advance(task);return claim(task,row,true);
    }
    /** Transport must prove its worker rejected the operation before any file I/O; timeouts and ordinary errors do not qualify. */
    public void releaseUnstartedPart(Claim claim){
        if(claim==null || !claim.execute())throw invalid();budgets.lock();var task=owned(claim.uploadId());
        if(!Set.of("INITIATED","UPLOADING").contains(task.getState()))return;
        var part=parts.selectOne(new LambdaQueryWrapper<UploadPartEntity>().eq(UploadPartEntity::getTaskId,task.getId()).eq(UploadPartEntity::getPartNumber,claim.number()).last("FOR UPDATE"));
        if(part!=null && part.getState().equals("RECEIVING") && part.getClaimId().equals(claim.claimId())){part.setState("RETRYABLE");parts.updateById(part);advance(task);}
    }
    public Freeze claimFreeze(String id){
        budgets.lock();var task=owned(id);if(!task.getState().equals("VERIFYING"))throw state();
        boolean execute=task.getFreezeClaim()==null;if(execute){task.setFreezeClaim(UUID.randomUUID().toString());advance(task);}
        var stored=parts.selectList(new LambdaQueryWrapper<UploadPartEntity>().eq(UploadPartEntity::getTaskId,task.getId()).orderByAsc(UploadPartEntity::getPartNumber).last("LIMIT 10001 FOR SHARE"));
        if(stored.size()!=task.getPartCount())throw state();
        for(int i=0;i<stored.size();i++)if(stored.get(i).getPartNumber()!=i+1 || !stored.get(i).getState().equals("STORED") || stored.get(i).getStorageReceipt()==null)throw state();
        return new Freeze(id,task.getId(),snapshot(task).configuration(),task.getFreezeClaim(),execute,task.getExpectedSize(),task.getExpectedChecksum(),
                stored.stream().map(row->new FrozenPart(row.getPartNumber(),row.getSizeBytes(),row.getChecksum(),row.getStorageReceipt())).toList());
    }
    public View confirmFrozen(Freeze freeze,long size,String checksum,String receipt){
        if(freeze==null || receipt==null || !receipt.matches("frozen-[a-f0-9-]{36}\\.blob"))throw invalid();budgets.lock();var task=owned(freeze.uploadId());
        if(task.getId()!=freeze.taskId() || !Objects.equals(task.getFreezeClaim(),freeze.claimId()) || task.getExpectedSize()!=size || !task.getExpectedChecksum().equals(checksum))throw new BizException(FileErrorCode.UPLOAD_CONFLICT);
        if(task.getState().equals("SCANNING") && Objects.equals(task.getFrozenReceipt(),receipt))return view(task);
        if(!task.getState().equals("VERIFYING"))throw state();task.setFrozenReceipt(receipt);task.setFrozenChecksum(checksum);task.setState("SCANNING");advance(task);return view(task);
    }
    public void releaseUnstartedFreeze(Freeze claim){
        if(claim==null || !claim.execute())throw invalid();budgets.lock();var task=owned(claim.uploadId());
        if(task.getState().equals("VERIFYING") && Objects.equals(task.getFreezeClaim(),claim.claimId())){task.setFreezeClaim(null);advance(task);}
    }
    /** Cancellation only schedules cleanup. Reserved bytes remain charged until a later verified deletion transition. */
    public View abort(String id,long expectedVersion){
        budgets.lock();var task=owned(id);if(task.getState().equals("ABORTING"))return view(task);
        if(task.getVersion()!=expectedVersion)throw new BizException(FileErrorCode.STORAGE_VERSION_CONFLICT);
        if(!Set.of("INITIATED","UPLOADING").contains(task.getState()))throw state();task.setState("ABORTING");advance(task);return view(task);
    }
    /** The next phase freezes bytes; this transition alone never creates a readable asset. */
    public View requestVerification(String id,long expectedVersion){
        budgets.lock();var task=owned(id);if(task.getState().equals("VERIFYING"))return view(task);
        writable(task);if(task.getVersion()!=expectedVersion)throw new BizException(FileErrorCode.STORAGE_VERSION_CONFLICT);
        if(!task.getReceivedParts().equals(task.getPartCount()) || !task.getReceivedBytes().equals(task.getExpectedSize()))throw state();
        task.setState("VERIFYING");advance(task);return view(task);
    }
    /** Maintenance only schedules bounded cleanup; it cannot restart a part write or release an unverified reservation. */
    public int expire(){
        if(ActorContext.require().kind()!=ActorRef.Kind.SYSTEM_JOB)throw new AccessDeniedException("File maintenance job required");
        if(budgets.existing()==null)return 0;
        var rows=tasks.selectList(new LambdaQueryWrapper<UploadTaskEntity>().in(UploadTaskEntity::getState,Set.of("INITIATED","UPLOADING"))
                .le(UploadTaskEntity::getExpiresAt,clock.now()).orderByAsc(UploadTaskEntity::getId).last("LIMIT 100 FOR UPDATE"));
        for(var row:rows){row.setState("EXPIRED");advance(row);}return rows.size();
    }
    private record Snapshot(StorageSceneService.Resolved configuration,FileSceneAccess access){}
    private UploadTaskEntity owned(String id){
        if(id==null || !id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))throw new BizException(FileErrorCode.UPLOAD_NOT_FOUND);
        var row=tasks.selectOne(new LambdaQueryWrapper<UploadTaskEntity>().eq(UploadTaskEntity::getPublicId,id).last("FOR UPDATE"));
        if(row==null || !json.readValue(row.getOwnerJson(),Owner.class).equals(owner()))throw new BizException(FileErrorCode.UPLOAD_NOT_FOUND);validateLive(row);return row;
    }
    private void validateLive(UploadTaskEntity row){var snapshot=snapshot(row);scenes.validateUpload(snapshot.configuration(),snapshot.access());}
    private Snapshot snapshot(UploadTaskEntity row){return json.readValue(row.getSnapshotJson(),Snapshot.class);}
    private void writable(UploadTaskEntity row){if(!row.getExpiresAt().isAfter(clock.now()))throw new BizException(FileErrorCode.UPLOAD_EXPIRED);if(!Set.of("INITIATED","UPLOADING").contains(row.getState()))throw state();}
    private Owner owner(){var actor=ActorContext.require();String portal=actor.kind()==ActorRef.Kind.ADMIN_USER?CurrentUser.require().portal():null;return new Owner(actor,portal);}
    private Fingerprint validate(Input input){
        if(input==null || input.key()==null || !input.key().matches("[A-Za-z0-9_.:-]{16,128}") || input.access()==null || input.access().action()!=FileSceneAccess.Action.UPLOAD
                || input.access().fileId()!=null || input.mode()==null || input.size()<1 || input.size()>20L*1024*1024*1024 || !checksum(input.checksum()))throw invalid();
        return new Fingerprint(input.access(),input.preferredProfile(),input.mode(),FileNameRules.normalize(input.name(),input.format()),input.format(),input.size(),input.checksum());
    }
    private boolean checksum(String value){return value!=null && value.matches("[0-9a-f]{64}");}
    private Claim claim(UploadTaskEntity task,UploadPartEntity part,boolean execute){return new Claim(task.getPublicId(),task.getId(),task.getFileId(),snapshot(task).configuration(),part.getPartNumber(),part.getSizeBytes(),part.getChecksum(),part.getClaimId(),execute,part.getState());}
    private Part part(UploadPartEntity row){return new Part(row.getPartNumber(),row.getSizeBytes(),row.getChecksum(),row.getState());}
    private void advance(UploadTaskEntity row){row.setVersion(Math.addExact(row.getVersion(),1));tasks.updateById(row);}
    private View view(UploadTaskEntity row){return new View(row.getPublicId(),row.getFileId(),row.getState(),row.getVersion(),row.getOriginalName(),FileFormat.valueOf(row.getFormat()),UploadMode.valueOf(row.getMode()),row.getExpectedSize(),row.getPartBytes(),row.getPartCount(),row.getReceivedParts(),row.getReceivedBytes(),row.getExpiresAt());}
    private TenantConfigurationRef reference(UploadTaskEntity row){return new TenantConfigurationRef(row.getTenantId(),"file",row.getId(),1,1);}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private BizException invalid(){return new BizException(FileErrorCode.INVALID_UPLOAD);}
    private BizException state(){return new BizException(FileErrorCode.UPLOAD_STATE);}
}
