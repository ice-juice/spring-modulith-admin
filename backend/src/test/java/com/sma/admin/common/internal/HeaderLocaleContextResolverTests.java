package com.sma.admin.common.internal;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
class HeaderLocaleContextResolverTests {
 @Test void explicitlyRejectedLanguagesAreNotSelectedByTheLanguageFallback(){
  var resolver=new HeaderLocaleContextResolver(new I18nProperties(null,null,"UTC"));
  var request=new MockHttpServletRequest();request.addHeader("Accept-Language","fr-FR;q=1,en-US;q=0");
  assertThat(resolver.resolveLocale(request).toLanguageTag()).isEqualTo("zh-CN");
 }
}
