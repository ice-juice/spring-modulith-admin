package com.sma.admin.extension;

/** UNKNOWN requires reconciliation; a timeout never causes an automatic second business write. */
public record ExtensionWork(long id,String state,long releaseRevision,String bundleId,byte[] result,String failure) {
    public ExtensionWork {result=result==null?null:result.clone();}
    @Override public byte[] result(){return result==null?null:result.clone();}
}
