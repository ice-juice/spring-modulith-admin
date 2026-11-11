package com.sma.admin.common.request;

/** Trusted module code builds this command; never bind it directly from HTTP JSON. */
public record RequestCommand(String operation, String conflictGroup, String resource, String key,
                             byte[] input, String permission) {
    public RequestCommand {
        if (operation == null || !operation.matches("[a-z][a-z0-9.-]{1,95}")
                || conflictGroup == null || !conflictGroup.matches("[a-z][a-z0-9.-]{1,95}")
                || resource == null || resource.isBlank() || resource.length() > 256
                || key == null || !key.matches("[A-Za-z0-9_.:-]{16,128}")
                || input == null || input.length > 65536
                || permission == null || (!permission.isEmpty() && !permission.matches("[a-z]+(:[a-z][a-z0-9-]*){1,3}")))
            throw new IllegalArgumentException("Invalid request command");
        input = input.clone();
    }
    @Override public byte[] input() { return input.clone(); }
    @Override public String toString() { return "RequestCommand[operation=" + operation + ", input=REDACTED, key=REDACTED]"; }
}
