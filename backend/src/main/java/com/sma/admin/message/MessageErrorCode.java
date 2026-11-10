package com.sma.admin.message;
import com.sma.admin.common.ErrorCode;
public enum MessageErrorCode implements ErrorCode {
 EMPTY_AUDIENCE("message.error.empty-audience",400),INVALID_STATE("message.error.invalid-state",409);
 private final String key;private final int status;
 MessageErrorCode(String key,int status){this.key=key;this.status=status;}
 public String key(){return key;}public int status(){return status;}
}
