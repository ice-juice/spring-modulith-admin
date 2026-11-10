package com.sma.admin.auth;
/** Read-only session validity for authenticated transports, within the current trusted tenant. */
public interface SessionQuery {
 boolean active(long sessionId,long userId,String portal);
}
