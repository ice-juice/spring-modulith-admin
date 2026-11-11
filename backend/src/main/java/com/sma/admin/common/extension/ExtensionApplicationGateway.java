package com.sma.admin.common.extension;

/** Public transport adapter. Implementation resolves the already authenticated application identity itself. */
public interface ExtensionApplicationGateway {
    Result invoke(String deploymentId,String pluginId,String entryId,String workKey,byte[] input);
    record Result(String workId,String state,String releaseRevision,String bundleId,byte[] json,String failure) {
        public Result {json=json==null?null:json.clone();}
        @Override public byte[] json(){return json==null?null:json.clone();}
    }
}
