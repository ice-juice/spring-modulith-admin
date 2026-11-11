package com.sma.admin.file;
import com.sma.admin.common.ErrorCode;
public enum FileErrorCode implements ErrorCode {
 TOO_LARGE("file.error.too-large",413),STORAGE("file.error.storage",500),INVALID_IMAGE("file.error.invalid-image",400),IN_USE("file.error.in-use",400),
 INVALID_STORAGE_CONFIGURATION("file.error.invalid-storage-configuration",400),STORAGE_PROFILE_NOT_FOUND("file.error.storage-profile-not-found",404),
 STORAGE_PROFILE_CONFLICT("file.error.storage-profile-conflict",409),STORAGE_VERSION_CONFLICT("file.error.storage-version-conflict",409),
 INVALID_SCENE("file.error.invalid-scene",400),SCENE_NOT_FOUND("file.error.scene-not-found",404),SCENE_CONFLICT("file.error.scene-conflict",409),
 INVALID_UPLOAD("file.error.invalid-upload",400),UPLOAD_NOT_FOUND("file.error.upload-not-found",404),UPLOAD_CONFLICT("file.error.upload-conflict",409),
 UPLOAD_LIMIT("file.error.upload-limit",429),UPLOAD_STATE("file.error.upload-state",409),UPLOAD_EXPIRED("file.error.upload-expired",410);
 private final String key;private final int status;
 FileErrorCode(String key,int status){this.key=key;this.status=status;}
 @Override public String key(){return key;}@Override public int status(){return status;}
}
