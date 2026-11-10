package com.sma.admin.openapi;
import com.sma.admin.common.ErrorCode;
public enum OpenapiErrorCode implements ErrorCode {
 DUPLICATE_NAME("openapi.error.duplicate-name",400),LINKED("openapi.error.linked",400),SECRET_STORAGE("openapi.error.secret-storage",500),INVALID_CREDENTIALS("openapi.error.invalid-credentials",401),INVALID_SIGNATURE("openapi.error.invalid-signature",403),REPLAY("openapi.error.replay",409),RATE_LIMIT("openapi.error.rate-limit",429);
 private final String key;private final int status;OpenapiErrorCode(String key,int status){this.key=key;this.status=status;}public String key(){return key;}public int status(){return status;}
}
