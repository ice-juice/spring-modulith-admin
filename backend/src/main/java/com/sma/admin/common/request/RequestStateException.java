package com.sma.admin.common.request;

public class RequestStateException extends RuntimeException {
    private final RequestReceipt receipt;
    public RequestStateException(RequestReceipt receipt) { super("Existing operation receipt"); this.receipt = receipt; }
    public RequestReceipt receipt() { return receipt; }
    public RequestRejected.Reason reason() {
        return switch (receipt.state()) {
            case RUNNING -> RequestRejected.Reason.PROCESSING;
            case UNKNOWN -> RequestRejected.Reason.UNKNOWN;
            case SUCCEEDED -> RequestRejected.Reason.COMPLETED;
            case FAILED_FINAL -> RequestRejected.Reason.FAILED;
        };
    }
}
