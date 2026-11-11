package com.sma.admin.common.internal.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sma.admin.common.request.RequestRejected;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.*;
import tools.jackson.databind.json.JsonMapper;

/** Private input serializer. Response serialization must keep WRITE_ONLY fields hidden. */
@Component
public class RequestInputFingerprint {
    private final JsonMapper inputs;
    public RequestInputFingerprint(JsonMapper json){
        var original=json.serializationConfig().getAnnotationIntrospector();
        var includeWriteOnly=new NopAnnotationIntrospector(){
            @Override public JsonProperty.Access findPropertyAccess(MapperConfig<?> config,Annotated property){
                return original.findPropertyAccess(config,property)==JsonProperty.Access.WRITE_ONLY?JsonProperty.Access.READ_WRITE:null;
            }
        };
        inputs=json.rebuild().annotationIntrospector(AnnotationIntrospector.pair(includeWriteOnly,original)).build();
    }
    public byte[] digest(Object envelope){
        byte[] bytes=inputs.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(envelope);
        try{
            if(bytes.length>1048576)throw new RequestRejected(RequestRejected.Reason.TOO_LARGE,0);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).getBytes(StandardCharsets.US_ASCII);
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        finally{Arrays.fill(bytes,(byte)0);}
    }
}
