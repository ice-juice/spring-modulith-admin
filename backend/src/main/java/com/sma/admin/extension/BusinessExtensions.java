package com.sma.admin.extension;

/** Host application calls enter through current authenticated identity; no caller-selected tenant or implementation. */
public interface BusinessExtensions {
    byte[] invoke(String deploymentId, String pluginId, String entryId, byte[] json);
    ExtensionWork invokeWork(String deploymentId,String pluginId,String entryId,String workKey,byte[] json);
    ExtensionWork work(long id);
    /** Background adapters only; refuses an attached HTTP/user authentication context. */
    ExtensionWork invokeJob(String policyId,long trustedTenantId,String objectId);
}
