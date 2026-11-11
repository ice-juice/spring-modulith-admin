package com.sma.admin.file;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

/** Tenant-scoped upload tasks for the current authenticated actor. Scene policies authorize every call.
 * Byte transfer and completion must run outside a caller transaction. The API closes the supplied stream.
 * SCANNING is quarantined, not a readable file. Cancellation schedules cleanup and does not release bytes immediately.
 */
public interface FileUploadApi {
    record Command(String key,String application,String scene,String objectType,String objectId,Long preferredProfile,
            UploadMode mode,String name,FileFormat format,long size,String checksum) {
        @Override public String toString(){return "FileUploadCommand[REDACTED]";}
    }
    record Task(String uploadId,Long fileId,String state,Long version,String name,FileFormat format,UploadMode mode,
            long expectedSize,long partBytes,int partCount,int receivedParts,long receivedBytes,Instant expiresAt) {}
    record Part(int number,long size,String checksum,String state) {}
    Task initiate(Command command);
    Task status(String uploadId);
    List<Part> parts(String uploadId,int after,int limit);
    Task putPart(String uploadId,int number,long size,String checksum,InputStream input);
    Task complete(String uploadId,long version);
    Task abort(String uploadId,long version);
}
