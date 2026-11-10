package com.sma.admin.openapi;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.openapi.internal.service.SignatureVerifier;
class SignatureProtocolTests {
 @Test void canonicalQueryMatchesTheRfc3986ClientVectorIncludingRepeatedAndEmptySegments(){
  assertThat(SignatureVerifier.canonicalQuery("&&z=last&a=two+words&a=one&q=%E4%BD%A0%E5%A5%BD&star=*&"))
   .isEqualTo("a=one&a=two%20words&q=%E4%BD%A0%E5%A5%BD&star=%2A&z=last");
  assertThat(SignatureVerifier.canonicalQuery("=&&x&")).isEqualTo("=&x=");
 }
}
