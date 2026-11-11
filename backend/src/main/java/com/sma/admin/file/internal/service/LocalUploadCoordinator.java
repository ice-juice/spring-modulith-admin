package com.sma.admin.file.internal.service;

import java.io.*;
import java.util.Locale;
import com.sma.admin.common.BizException;
import com.sma.admin.file.FileErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Coordinates short task commits with immutable file operations. Never runs under an enclosing business transaction. */
@Service
public class LocalUploadCoordinator {
    private final UploadTaskStore tasks;private final LocalStorageLocation locations;private final LocalTransferStorage storage;
    public LocalUploadCoordinator(UploadTaskStore tasks,LocalStorageLocation locations,LocalTransferStorage storage){this.tasks=tasks;this.locations=locations;this.storage=storage;}
    public UploadTaskStore.View part(String id,int number,long size,String checksum,InputStream input){
        outside();if(input==null)throw new BizException(FileErrorCode.INVALID_UPLOAD);
        try(input){
            var claim=tasks.beginPart(id,number,size,checksum);var location=locations.resolve(claim.snapshot());var object=object(location,claim);
            LocalTransferStorage.Stored stored;
            if(claim.execute())stored=write(claim,object,input);
            else{
                var inspection=storage.inspect(object,claim.size(),claim.checksum());
                switch(inspection.presence()){
                    case ABSENT,BUSY->{return tasks.status(id);}
                    case MATCH->stored=inspection.stored();
                    case MISMATCH->{
                        if(claim.state().equals("STORED"))throw new BizException(FileErrorCode.UPLOAD_CONFLICT);
                        claim=tasks.retryClosedPartial(claim);object=object(location,claim);stored=write(claim,object,input);
                    }
                    default->throw new IllegalStateException("Unknown local inspection");
                }
            }
            return tasks.confirmPart(claim,stored.size(),stored.checksum(),object.name());
        }catch(IOException failure){throw new BizException(FileErrorCode.STORAGE);}
    }
    public UploadTaskStore.View complete(String id,long version){
        outside();var status=tasks.status(id);if(status.state().equals("SCANNING"))return status;
        tasks.requestVerification(id,version);var claim=tasks.claimFreeze(id);var location=locations.resolve(claim.snapshot());
        var object=new LocalTransferStorage.ObjectRef(location,id,"frozen-"+claim.claimId()+".blob");LocalTransferStorage.Stored stored;
        if(claim.execute()){
            var parts=claim.parts().stream().map(part->new LocalTransferStorage.Expected(new LocalTransferStorage.ObjectRef(location,id,part.receipt()),part.size(),part.checksum())).toList();
            try{stored=storage.freeze(object,parts,claim.size(),claim.checksum());}
            catch(LocalTransferStorage.NotStarted rejected){tasks.releaseUnstartedFreeze(claim);throw rejected;}
        }else{
            var inspection=storage.inspect(object,claim.size(),claim.checksum());
            if(inspection.presence()==LocalTransferStorage.Presence.ABSENT || inspection.presence()==LocalTransferStorage.Presence.BUSY)return tasks.status(id);
            if(inspection.presence()!=LocalTransferStorage.Presence.MATCH)throw new BizException(FileErrorCode.UPLOAD_CONFLICT);stored=inspection.stored();
        }
        return tasks.confirmFrozen(claim,stored.size(),stored.checksum(),object.name());
    }
    private LocalTransferStorage.ObjectRef object(LocalStorageLocation.Location location,UploadTaskStore.Claim claim){
        return new LocalTransferStorage.ObjectRef(location,claim.uploadId(),String.format(Locale.ROOT,"part-%05d-%s.blob",claim.number(),claim.claimId()));
    }
    private LocalTransferStorage.Stored write(UploadTaskStore.Claim claim,LocalTransferStorage.ObjectRef object,InputStream input){
        try{return storage.write(object,input,claim.size(),claim.checksum());}
        catch(LocalTransferStorage.NotStarted rejected){tasks.releaseUnstartedPart(claim);throw rejected;}
    }
    private void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Local upload coordinator requires no outer transaction");}
}
