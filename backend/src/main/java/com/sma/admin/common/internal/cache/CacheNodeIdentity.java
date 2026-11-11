package com.sma.admin.common.internal.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Process lifetime only; never a Snowflake worker assignment or an authentication credential. */
@Component @EnableConfigurationProperties(CacheNodeProperties.class)
public class CacheNodeIdentity {
    private final CacheNodeProperties properties;
    private final String processId=UUID.randomUUID().toString();
    public CacheNodeIdentity(CacheNodeProperties properties){this.properties=properties;}
    public CacheNodeProperties properties(){return properties;}
    public String processId(){return processId;}
    public String clusterHash(){return digest(properties.cluster());}
    public String manifestHash(){return digest(properties.cluster()+"\n"+properties.version()+"\n"+String.join("\n",properties.expected()));}
    private static String digest(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
