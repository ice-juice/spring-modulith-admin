package com.sma.admin.common.extension;

/** Stable business-owned adapter. Never implemented or selected by an uploaded plugin or request body. */
public interface ExtensionWorkReconciler {
    String pluginId();
    String entryId();
    /** Resolve against authoritative business records; terminal outcomes MUST fence any late writer first. */
    Resolution reconcile(Work work);
    record Work(String id,long tenantId,String subject,String portal,String deploymentId,String pluginId,String entryId,
            long releaseRevision,String bundleId,String inputSha256,String claimId) {}
    enum Outcome { PENDING, SUCCEEDED, CANCELLED }
    record Resolution(Outcome outcome,byte[] result,String evidenceSha256) {
        public Resolution { if(outcome==null)throw new IllegalArgumentException("Outcome required");result=result==null?null:result.clone(); }
        @Override public byte[] result(){return result==null?null:result.clone();}
    }
}
