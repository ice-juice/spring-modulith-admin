package com.sma.admin.common.internal.security;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

class RequestInputFingerprintTests {
    record Secret(@JsonProperty(value="credential",access=JsonProperty.Access.WRITE_ONLY) char[] password,String name){}
    record Nested(Secret configuration){}
    @Test void nestedWriteOnlyInputsAffectFingerprintWhileResponsesStayRedacted(){
        var json=JsonMapper.builder().build();var fingerprints=new RequestInputFingerprint(json);
        var first=new Nested(new Secret("first-private-value".toCharArray(),"same"));
        var changed=new Nested(new Secret("other-private-value".toCharArray(),"same"));
        assertThat(fingerprints.digest(first)).isNotEqualTo(fingerprints.digest(changed));
        assertThat(fingerprints.digest(first)).isEqualTo(fingerprints.digest(first));
        assertThat(new String(fingerprints.digest(first),StandardCharsets.US_ASCII)).hasSize(64).doesNotContain("private-value");
        assertThat(json.writeValueAsString(first)).doesNotContain("credential","password","private-value");
        assertThat(first.configuration().password()).containsExactly("first-private-value".toCharArray());
    }
    @Test void ordinaryCanonicalFingerprintRemainsCompatible()throws Exception{
        var json=JsonMapper.builder().build();var fingerprints=new RequestInputFingerprint(json);
        var input=Map.of("path",Map.of("id","123"),"query",Map.of("p",List.of("a")),"body",List.of(Map.of("name","ordinary","version",2)));
        byte[] old=json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(input);
        assertThat(new String(fingerprints.digest(input),StandardCharsets.US_ASCII)).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(old)));
    }
}
