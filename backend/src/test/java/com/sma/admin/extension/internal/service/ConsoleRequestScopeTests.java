package com.sma.admin.extension.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import com.sma.admin.common.BizException;
import com.sma.admin.extension.internal.web.ExtensionConsoleController;

class ConsoleRequestScopeTests {
    private final ExtensionConsoleController controller=new ExtensionConsoleController(mock(ConsoleProxyService.class));
    @Test void onlyActualAllowlistedReadsAreQueriesAndDistinctChunksHaveDistinctScopes() {
        assertThat(controller.readOnlyRequest("request",input("GET","/console/jobs"))).isTrue();
        assertThat(controller.readOnlyRequest("request",input("GET","/uploads/upload-one"))).isTrue();
        assertThatThrownBy(()->controller.readOnlyRequest("request",input("POST","/uploads/upload-one"))).isInstanceOf(BizException.class);
        assertThat(controller.readOnlyRequest("request",input("POST","/commands"))).isFalse();
        assertThatThrownBy(()->controller.readOnlyRequest("request",input("GET","/commands"))).isInstanceOf(BizException.class);
        assertThatThrownBy(()->controller.readOnlyRequest("request",input("GET","https://evil.test/groups"))).isInstanceOf(BizException.class);
        var one=controller.requestResources("request",input("PUT","/uploads/task-one/files/0/chunks/1"));
        var two=controller.requestResources("request",input("PUT","/uploads/task-one/files/0/chunks/2"));
        assertThat(one).isNotEqualTo(two);
        assertThat(controller.requestResources("request",input("PUT","/uploads/task-one/files/00/chunks/000001"))).isEqualTo(one);
        assertThat(controller.requestResources("request",input("GET","/console/jobs?offset=0")))
                .isEqualTo(controller.requestResources("request",input("GET","/console/jobs?offset=20")));
        assertThat(one.values()).allMatch(value->value.matches("[0-9a-f]{64}"));
    }
    private Object[] input(String method,String path){
        var request=new MockHttpServletRequest("POST","/api/extension/consoles/requests");
        request.addHeader("X-Release-Method",method);request.addHeader("X-Release-Path",path);
        request.addHeader("X-Request-Safe","true"); // Untrusted hints cannot turn commands into reads.
        return new Object[]{request};
    }
}
