package com.sma.admin.extension.internal.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import com.sma.admin.common.extension.ExtensionApplicationPolicy;
import com.sma.admin.common.security.*;
import com.sma.admin.support.Users;
import com.sma.extension.api.InvocationIdentity;

class ApplicationExtensionTests {
    @Test void authenticatedApplicationStillRequiresBusinessOwnedGrantAndScopeDoesNotLeak() {
        var runtime=mock(ExtensionRuntimeService.class);
        var denied=new ApplicationExtensionService(runtime,List.of());
        Users.as(SmaPrincipal.forApplication(72,"admin","client-a"),() -> {assertThatThrownBy(() -> denied.invoke("demo","example","run",null,"{}".getBytes())).isInstanceOf(AccessDeniedException.class);return null;});
        var policy=new ExtensionApplicationPolicy(){public String pluginId(){return "example";}public Set<String> permissions(String app,long tenant,String portal){return app.equals("client-a") && tenant==72 && portal.equals("admin")?Set.of("extension:invoke:example"):Set.of();}};
        var service=new ApplicationExtensionService(runtime,List.of(policy));
        when(runtime.invokeScoped(eq("demo"),eq("example"),eq("run"),any())).thenAnswer(invocation -> {
            var actor=WorkCaller.current();assertThat(actor.kind()).isEqualTo(InvocationIdentity.Kind.APPLICATION);assertThat(actor.subject()).isEqualTo("app:client-a");assertThat(actor.permissions()).containsExactly("extension:invoke:example");return "{}".getBytes();
        });
        Users.as(SmaPrincipal.forApplication(72,"admin","client-a"),() -> {assertThat(service.invoke("demo","example","run",null,"{}".getBytes()).state()).isEqualTo("SUCCEEDED");assertThatThrownBy(WorkCaller::current).isInstanceOf(AccessDeniedException.class);return null;});
        Users.as(SmaPrincipal.forApplication(73,"admin","client-a"),() -> {assertThatThrownBy(() -> service.invoke("demo","example","run",null,"{}".getBytes())).isInstanceOf(AccessDeniedException.class);return null;});
        Users.as(Users.principal(1,72,DataAccess.unrestricted()),() -> {assertThatThrownBy(() -> service.invoke("demo","example","run",null,"{}".getBytes())).isInstanceOf(AccessDeniedException.class);return null;});
        verify(runtime,times(1)).invokeScoped(any(),any(),any(),any());
    }
}
