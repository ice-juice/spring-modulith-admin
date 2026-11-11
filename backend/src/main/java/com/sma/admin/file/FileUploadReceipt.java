package com.sma.admin.file;

import java.time.Instant;

/** Actor-owned metadata only. An accepted upload has not yet written a file. */
public record FileUploadReceipt(String uploadId, String state, Long fileId, boolean resumable,
                                Instant createdAt, Instant updatedAt) {}
