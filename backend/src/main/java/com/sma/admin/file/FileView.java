package com.sma.admin.file;
import java.time.Instant;
public record FileView(Long id,String name,String contentType,String kind,long sizeBytes,String checksum,Long ownerId,String uploader,
 Instant createdAt,boolean previewable,String contentPath){}
