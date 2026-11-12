package com.sma.admin.common.internal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.sma.admin.common.*;
import com.sma.admin.common.internal.security.RequestRejectionSignals;
import com.sma.admin.common.security.SecuritySignal;
import tools.jackson.databind.json.JsonMapper;
class RequestRejectionResponseTests {
    @Test void legacyWriterImplementationRetainsDefaultDelegation()throws Exception{
        ErrorResponseWriter legacy=(request,response,error)->response.setStatus(error.status());
        var response=new MockHttpServletResponse();
        legacy.writeObserved(new MockHttpServletRequest(),response,CommonErrorCode.FORBIDDEN,SecuritySignal.Fact.BROWSER_ORIGIN_REJECTED);
        assertThat(response.getStatus()).isEqualTo(403);
    }
    @Test void fixedObservationDoesNotAlterResponseBodyHeadersAndMvcBusinessProtocol()throws Exception{
        var signals=mock(RequestRejectionSignals.class);var json=JsonMapper.builder().build();
        var messages=new StaticMessageSource();messages.addMessage("common.error.forbidden",Locale.ENGLISH,"Denied");
        var writer=new LocalizedErrors(messages,new FixedLocaleResolver(Locale.ENGLISH),json,signals);
        var request=new MockHttpServletRequest();request.addPreferredLocale(Locale.ENGLISH);
        var ordinary=new MockHttpServletResponse();var observed=new MockHttpServletResponse();
        ordinary.setHeader("Retry-After","2");observed.setHeader("Retry-After","2");
        writer.write(request,ordinary,CommonErrorCode.FORBIDDEN);
        writer.writeObserved(request,observed,CommonErrorCode.FORBIDDEN,SecuritySignal.Fact.BROWSER_ORIGIN_REJECTED);
        assertThat(observed.getStatus()).isEqualTo(403);assertThat(observed.getContentAsByteArray()).isEqualTo(ordinary.getContentAsByteArray());
        assertThat(observed.getContentType()).isEqualTo(ordinary.getContentType());assertThat(observed.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(observed.getHeaderNames()).containsExactlyInAnyOrderElementsOf(ordinary.getHeaderNames());
        assertThat(observed.getHeader("Retry-After")).isEqualTo("2");
        verify(signals).observed(SecuritySignal.Fact.BROWSER_ORIGIN_REJECTED,403);
        var handler=new GlobalExceptionHandler(writer);var body=handler.handleBiz(new BizException(CommonErrorCode.UNAUTHORIZED));
        assertThat(body.getStatusCode().value()).isEqualTo(200);assertThat(json.writeValueAsString(body.getBody())).contains("common.error.unauthorized");
        verify(signals).response(CommonErrorCode.UNAUTHORIZED,200);
    }
}
