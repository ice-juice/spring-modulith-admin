package com.sma.admin.identity.internal.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.sma.admin.common.security.*;
import com.sma.admin.identity.internal.persistence.IdentityClientRevisionEntity;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GoogleProtocolTests {
    private IdentityClientRevisionEntity configuration(){var row=new IdentityClientRevisionEntity();row.setTenantId(981L);row.setClientId(31L);
        row.setConfigurationVersion(2L);row.setCredentialVersion(1L);row.setGoogleEnabled(true);row.setGoogleClientId("123456-app.apps.googleusercontent.com");
        row.setCallbacksJson("[\"https://app.example.test/oauth/callback\"]");row.setSecretEnvelope("encrypted-secret");return row;}
    @Test void authorizationUsesOnlyRegisteredCallbackAndPkceS256WithoutOfflineAccess(){
        var transport=mock(GoogleHttpTransport.class);var verifier=mock(GoogleIdTokenVerifier.class);var secrets=mock(TenantSecretCodec.class);
        var protocol=new GoogleProtocol(transport,verifier,secrets,JsonMapper.builder().build());var configuration=configuration();
        assertThat(GoogleProtocol.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
        ActorContext.withinJob(981,"google-login",()->{
            String url=protocol.authorization(configuration,"https://app.example.test/oauth/callback","s".repeat(43),"n".repeat(43),"p".repeat(43)).toString();
            assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?").contains("code_challenge_method=S256","scope=openid+email+profile")
                    .doesNotContain("access_type=offline","client_secret");
            assertThatThrownBy(()->protocol.authorization(configuration,"https://evil.example/cb","s".repeat(43),"n".repeat(43),"p".repeat(43))).isInstanceOf(com.sma.admin.common.BizException.class);return null;
        });
        assertThatThrownBy(()->ActorContext.withinJob(982,"google-login",()->protocol.authorization(configuration,"https://app.example.test/oauth/callback","s".repeat(43),"n".repeat(43),"p".repeat(43))))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(transport,secrets,verifier);
    }
    @Test void exchangeDecryptsOnlyTheTenantSnapshotThenErasesItsSecretCopyAndVerifiesExpectedNonce(){
        var transport=mock(GoogleHttpTransport.class);var verifier=mock(GoogleIdTokenVerifier.class);var secrets=mock(TenantSecretCodec.class);
        var protocol=new GoogleProtocol(transport,verifier,secrets,JsonMapper.builder().build());var configuration=configuration();
        when(secrets.decrypt(new TenantConfigurationRef(981,"identity",31,2,1),"google-client-secret","encrypted-secret"))
                .thenReturn(new SecretValue("protected-google-secret".toCharArray()));
        var reference=new java.util.concurrent.atomic.AtomicReference<char[]>();
        when(transport.exchange(anyString(),any(),anyString(),anyString(),anyString())).thenAnswer(i->{
            char[] chars=i.getArgument(1);assertThat(chars).containsExactly("protected-google-secret".toCharArray());reference.set(chars);return "signed-id-token";
        });
        when(verifier.verify("signed-id-token",configuration.getGoogleClientId(),null,"n".repeat(43))).thenReturn(new GoogleIdTokenVerifier.Verified("https://accounts.google.com","subject",null,false));
        var result=ActorContext.withinJob(981,"google-login",()->protocol.exchange(configuration,"https://app.example.test/oauth/callback","code","v".repeat(43),"n".repeat(43)));
        assertThat(result.subject()).isEqualTo("subject");assertThat(reference.get()).containsOnly('\0');
        verify(verifier).verify("signed-id-token",configuration.getGoogleClientId(),null,"n".repeat(43));
    }
}
