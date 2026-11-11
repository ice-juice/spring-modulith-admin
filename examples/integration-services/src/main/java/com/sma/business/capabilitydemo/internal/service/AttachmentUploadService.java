package com.sma.business.capabilitydemo.internal.service;

import java.io.ByteArrayInputStream;
import java.security.*;
import java.util.HexFormat;
import com.sma.admin.file.*;
import org.springframework.stereotype.Service;

/** Small text example only. No outer @Transactional: bytes and persistent upload receipts have separate lifecycles. */
@Service
public final class AttachmentUploadService {
    private final FileUploadApi uploads;
    public AttachmentUploadService(FileUploadApi uploads){this.uploads=uploads;}
    public FileUploadApi.Task uploadSmallText(String requestKey,String documentId,String name,byte[] bytes,Long preferredProfile){
        if(bytes==null || bytes.length<1 || bytes.length>1048576)throw new IllegalArgumentException("Example accepts at most 1 MiB");
        String checksum;
        try{checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        var task=uploads.initiate(new FileUploadApi.Command(requestKey,"capabilitydemo","capabilitydemo.document","document",documentId,
                preferredProfile,UploadMode.SERVER,name,FileFormat.TEXT,bytes.length,checksum));
        if(!task.state().equals("INITIATED") && !task.state().equals("UPLOADING"))return task;
        task=uploads.putPart(task.uploadId(),1,bytes.length,checksum,new ByteArrayInputStream(bytes));
        if(task.receivedParts()!=task.partCount())return task; // Pending/unknown work is not permission to start another upload.
        return uploads.complete(task.uploadId(),task.version()); // SCANNING is not a successful business attachment.
    }
}
