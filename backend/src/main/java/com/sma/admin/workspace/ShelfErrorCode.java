package com.sma.admin.workspace;
import com.sma.admin.common.ErrorCode;
public enum ShelfErrorCode implements ErrorCode {
 CONFLICT("workspace.error.conflict",409), LIMIT("workspace.error.limit",400), UNAVAILABLE("workspace.error.unavailable",403);
 private final String key; private final int status;
 ShelfErrorCode(String key,int status){this.key=key;this.status=status;}
 public String key(){return key;} public int status(){return status;}
}
