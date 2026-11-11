package com.sma.admin.identity.internal.service;
import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class IdentityClientRulesTests {
    @Test void webAndNativeRedirectsAreExactAndDoNotAcceptWildcardsCredentialsOrFragments(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var rules=new IdentityClientRules(factory.getValidator());
            assertThat(rules.callback(IdentityClientService.Platform.WEB,"https://app.example.test/oauth/callback")).isTrue();
            assertThat(rules.callback(IdentityClientService.Platform.IOS,"com.example.shop:/oauth/callback")).isTrue();
            assertThat(rules.callback(IdentityClientService.Platform.ANDROID,"https://app.example.test/oauth/callback")).isTrue();
            for(String invalid:Set.of("http://app.example.test/callback","https://app.example.test/cb?next=evil","https://app.example.test/cb#fragment",
                    "https://user@app.example.test/cb","https://app.example.test/*","https://app.example.test/%0d%0a","https://app.example.test/../cb",
                    "javascript:alert(1)","com.example.shop:/oauth/callback","https://app.example.test:8443/cb","https://127.0.0.1/cb"))
                assertThat(rules.callback(IdentityClientService.Platform.WEB,invalid)).as(invalid).isFalse();
            for(String invalid:Set.of("https://app.example.test/","https://app.example.test/path","https://*.example.test","null","http://app.example.test"))
                assertThat(rules.origin(invalid)).as(invalid).isFalse();
            assertThat(rules.origin("https://app.example.test")).isTrue();
        }
    }
    @Test void nativeClientsCannotCarryServerSecretsAndWebNeedsOrigins(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var rules=new IdentityClientRules(factory.getValidator());
            var nativeClient=new IdentityClientService.Input(1L,"native","Native","shop",IdentityClientService.Platform.IOS,true,true,
                    "123456-test.apps.googleusercontent.com",null,Set.of("com.example.shop:/oauth/callback"),Set.of(),900,2592000);
            rules.validate(nativeClient);
            assertThatThrownBy(()->rules.validate(new IdentityClientService.Input(1L,"native","Native","shop",IdentityClientService.Platform.IOS,true,true,
                    nativeClient.googleClientId(),"not-an-app-secret".toCharArray(),nativeClient.callbackUris(),Set.of(),900,2592000))).isInstanceOf(com.sma.admin.common.BizException.class);
            assertThatThrownBy(()->rules.validate(new IdentityClientService.Input(1L,"web","Web","shop",IdentityClientService.Platform.WEB,true,true,
                    nativeClient.googleClientId(),null,Set.of("https://app.example.test/cb"),Set.of(),900,2592000))).isInstanceOf(com.sma.admin.common.BizException.class);
        }
    }
}
