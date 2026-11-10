package com.sma.admin.common.security;
/** A module grants access to a referenced asset without exposing its persistence layer to File. */
public interface FileAccessPolicy {
 default boolean canRead(long fileId,SmaPrincipal user){return false;}
 default boolean inUse(long fileId){return false;}
}
