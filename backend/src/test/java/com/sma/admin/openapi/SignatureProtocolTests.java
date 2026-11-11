package com.sma.admin.openapi;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sma.admin.openapi.internal.service.SignatureVerifier;
class SignatureProtocolTests {
 @Test void receiptRoutesCannotOptOutOfSigningThroughClientSettingsOrEncodedPath(){
  var nonces=org.mockito.Mockito.mock(com.sma.admin.openapi.internal.persistence.NonceMapper.class);
  var maintenance=org.mockito.Mockito.mock(com.sma.admin.openapi.internal.service.NonceMaintenance.class);
  var verifier=new SignatureVerifier(nonces,maintenance);
  var app=new com.sma.admin.openapi.internal.persistence.ApplicationEntity();app.setAppId("app");app.setSignatureRequired(false);
  for(String path:java.util.List.of("/open/mail/receipts","/open/%6dail/receipts","/open/mail/%72eceipts")){
   var proof=new com.sma.admin.openapi.internal.service.RequestProof("POST",path,null,null,null,null,null,"");
   assertThatThrownBy(()->verifier.verify(app,proof,"secret")).isInstanceOf(com.sma.admin.common.BizException.class);
  }
  verifier.verify(app,new com.sma.admin.openapi.internal.service.RequestProof("GET","/open/me",null,null,null,null,null,""),"secret");
  org.mockito.Mockito.verifyNoInteractions(nonces,maintenance);
 }
 @Test void canonicalQueryMatchesTheRfc3986ClientVectorIncludingRepeatedAndEmptySegments(){
  assertThat(SignatureVerifier.canonicalQuery("&&z=last&a=two+words&a=one&q=%E4%BD%A0%E5%A5%BD&star=*&"))
   .isEqualTo("a=one&a=two%20words&q=%E4%BD%A0%E5%A5%BD&star=%2A&z=last");
  assertThat(SignatureVerifier.canonicalQuery("=&&x&")).isEqualTo("=&x=");
 }
}
