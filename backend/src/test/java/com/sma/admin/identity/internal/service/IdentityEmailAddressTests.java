package com.sma.admin.identity.internal.service;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.BizException;

class IdentityEmailAddressTests {
    @Test void normalizesOnlyDomainAndNeverMergesLocalCaseDotsOrTags(){
        assertThat(IdentityEmailAddress.normalize("A.B+Tag@EXAMPLE.COM")).isEqualTo("A.B+Tag@example.com");
        assertThat(IdentityEmailAddress.normalize("User@bücher.de")).isEqualTo("User@xn--bcher-kva.de");
        assertThat(IdentityEmailAddress.normalize("A@a.com")).isNotEqualTo(IdentityEmailAddress.normalize("a@a.com"));
    }
    @Test void rejectsHeadersPathsQuotedLocalPartsInvalidDnsAndUnboundedInput(){
        for(String invalid:List.of(" a@example.com","a@example.com ","a@b.com\r\nBcc:x@b.com","a@@b.com","a..b@example.com",".a@example.com",
                "a.@example.com","\"a\"@example.com","a@localhost","a@[127.0.0.1]","a@bad..com","a@bad-.com","a@-bad.com","a@a.com/next","用户@example.com",
                "a".repeat(65)+"@example.com","a@"+"b".repeat(64)+".com"))
            assertThatThrownBy(()->IdentityEmailAddress.normalize(invalid)).as(invalid).isInstanceOf(BizException.class);
    }
}
