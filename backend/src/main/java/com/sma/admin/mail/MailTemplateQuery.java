package com.sma.admin.mail;

import java.util.Map;
import java.util.Optional;

/** Tenant-scoped published template metadata only; availability never grants permission to send. */
public interface MailTemplateQuery {
    Optional<Available> available(String application,String scene,String code,String language,Map<String,String> sampleVariables);
    record Available(Long templateId,Long publishedVersion,Long profileId) {}
}
