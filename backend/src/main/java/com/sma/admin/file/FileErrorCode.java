package com.sma.admin.file;
import com.sma.admin.common.ErrorCode;
public enum FileErrorCode implements ErrorCode {
 TOO_LARGE("file.error.too-large",413),STORAGE("file.error.storage",500),INVALID_IMAGE("file.error.invalid-image",400),IN_USE("file.error.in-use",400);
 private final String key;private final int status;
 FileErrorCode(String key,int status){this.key=key;this.status=status;}
 @Override public String key(){return key;}@Override public int status(){return status;}
}
