package com.sma.admin.file.internal.web;

import com.sma.admin.file.*;
import jakarta.validation.constraints.*;

/** Shared wire inputs; tenant, actor and storage object keys are never accepted from HTTP. */
public final class UploadTaskRequests {
    private UploadTaskRequests(){}
    public record Start(@NotBlank @Pattern(regexp="[a-z][a-z0-9.-]{0,63}")String application,
            @NotBlank @Size(max=96)String scene,@Size(max=64)String objectType,@Size(max=128)String objectId,
            @Positive Long preferredProfile,@NotNull UploadMode mode,@NotBlank @Size(max=255)String name,
            @NotNull FileFormat format,@Min(1) @Max(21474836480L)long size,@NotBlank @Pattern(regexp="[0-9a-f]{64}")String checksum){
        public FileUploadApi.Command command(String key){return new FileUploadApi.Command(key,application,scene,objectType,objectId,preferredProfile,mode,name,format,size,checksum);}
        @Override public String toString(){return "UploadTaskStart[REDACTED]";}
    }
    public record Version(@NotNull @Positive Long version){}
}
