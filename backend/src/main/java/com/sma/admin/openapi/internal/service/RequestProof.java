package com.sma.admin.openapi.internal.service;
public record RequestProof(String method,String path,String query,String timestamp,String nonce,String signature,String appId,String bodyHash){}
