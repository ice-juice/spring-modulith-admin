package com.sma.admin.mail.internal.service;

/** Persist only inside a tenant/delivery-bound encrypted envelope. */
public record MailPayload(String recipient, String subject, String body, boolean html) {
    @Override public String toString() { return "MailPayload[REDACTED]"; }
}
