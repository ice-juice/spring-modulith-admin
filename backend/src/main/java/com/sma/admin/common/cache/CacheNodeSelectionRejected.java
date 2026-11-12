package com.sma.admin.common.cache;
import com.sma.admin.common.ErrorCode;

public final class CacheNodeSelectionRejected extends RuntimeException {
    public enum Reason implements ErrorCode {
        INVALID("common.cache.selection-invalid",400),
        WRONG_NODE("common.cache.selection-wrong-node",503),
        REPLACED_PROCESS("common.cache.selection-replaced-process",409);
        private final String key;private final int status;
        Reason(String key,int status){this.key=key;this.status=status;}
        public String key(){return key;}public int status(){return status;}
    }
    private final Reason reason;
    public CacheNodeSelectionRejected(Reason reason){super(reason.key());this.reason=reason;}
    public Reason reason(){return reason;}
}
