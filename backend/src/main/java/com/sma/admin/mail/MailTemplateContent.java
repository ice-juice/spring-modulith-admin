package com.sma.admin.mail;

import java.util.Map;
import java.util.Set;

/** Declarative mail format. No expressions, scripts, includes or remote resources. */
public record MailTemplateContent(String subject, String body, boolean html,
                                  Map<String, Variable> variables, Set<String> linkHosts) {
    public enum Type { TEXT, INTEGER, DECIMAL, BOOLEAN, HTTPS_URL }
    public record Variable(Type type, int maxLength, boolean required) {}
    public MailTemplateContent {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        linkHosts = linkHosts == null ? Set.of() : Set.copyOf(linkHosts);
    }
    @Override public String toString() { return "MailTemplateContent[REDACTED]"; }
}
