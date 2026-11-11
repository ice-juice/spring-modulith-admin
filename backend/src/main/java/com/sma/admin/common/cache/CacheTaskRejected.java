package com.sma.admin.common.cache;

import com.sma.admin.common.ErrorCode;

public class CacheTaskRejected extends RuntimeException {
    public enum Reason implements ErrorCode {
        EXPIRED("common.cache-task.expired",409),
        CHANGED("common.cache-task.changed",409),
        EMPTY("common.cache-task.empty",400),
        LIMIT("common.cache-task.limit",429);
        private final String key;private final int status;
        Reason(String key,int status){this.key=key;this.status=status;}
        @Override public String key(){return key;}
        @Override public int status(){return status;}
    }
    private final Reason reason;
    public CacheTaskRejected(Reason reason){super(reason.key());this.reason=reason;}
    public Reason reason(){return reason;}
}
