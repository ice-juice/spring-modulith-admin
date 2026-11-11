package com.sma.admin.extension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.sma.admin.common.security.*;
import com.sma.admin.extension.internal.service.ConsoleProxyService;
import com.sma.admin.support.*;

@IntegrationTest @AutoConfigureMockMvc
@TestPropertySource(properties="sma.security.requests.minimum-write-interval=60s")
class ConsoleRequestGovernanceHttpTests {
    @Autowired MockMvc mvc;
    @MockitoBean ConsoleProxyService proxy;
    @Test void tunneledReadsAndDifferentChunksWorkWhileDuplicateChunkAndForgedReadAreRejected() {
        when(proxy.forward(any(),any(),any())).thenReturn(new ConsoleProxyService.Reply(200,"{\"code\":0}".getBytes(StandardCharsets.UTF_8)));
        var actor=new SmaPrincipal(79602,"console",79602,"admin",null,false,DataAccess.unrestricted(),Set.of("extension:console:view"),"UTC");
        var first=send(actor,"GET","/policy",new byte[0]);
        assertThat(first.getResponse().getStatus()).as(body(first)).isEqualTo(200);
        assertThat(first.getResponse().getHeader("X-Request-Policy")).isEqualTo("QUERY");
        assertThat(send(actor,"GET","/groups",new byte[0]).getResponse().getStatus()).isEqualTo(200);
        var chunk=send(actor,"PUT","/uploads/upload-one/files/0/chunks/0",new byte[]{11});
        assertThat(chunk.getResponse().getStatus()).as(body(chunk)).isEqualTo(200);
        assertThat(chunk.getResponse().getHeader("X-Request-Policy")).isEqualTo("NATIVE");
        assertThat(chunk.getResponse().getHeader("X-Operation-Id")).isNull();
        assertThat(send(actor,"PUT","/uploads/upload-one/files/0/chunks/1",new byte[]{12}).getResponse().getStatus()).isEqualTo(200);
        var duplicate=send(actor,"PUT","/uploads/upload-one/files/00/chunks/000000",new byte[]{11});
        assertThat(duplicate.getResponse().getStatus()).as(body(duplicate)).isEqualTo(429);
        assertThat(duplicate.getResponse().getHeader("Retry-After")).isNotBlank();
        var forged=send(actor,"GET","/commands",new byte[0]);
        assertThat(body(forged)).contains("extension.error.console-path-denied");
        var noPermission=new SmaPrincipal(79603,"no-console",79602,"admin",null,false,DataAccess.unrestricted(),Set.of(),"UTC");
        assertThat(send(noPermission,"GET","/policy",new byte[0]).getResponse().getStatus()).isEqualTo(403);
        var requests=ArgumentCaptor.forClass(ConsoleProxyService.Request.class);
        verify(proxy,times(4)).forward(requests.capture(),any(),any());
        assertThat(requests.getAllValues().get(2).body()).containsExactly((byte)11);
        assertThat(requests.getAllValues().get(3).body()).containsExactly((byte)12);
    }
    private MvcResult send(SmaPrincipal actor,String method,String path,byte[] bytes){return Users.as(actor,()->{
        try{return mvc.perform(post("/api/extension/consoles/requests").with(Users.request(actor))
                .header("X-Release-Method",method).header("X-Release-Path",path).header("X-Request-Safe","true")
                .contentType("PUT".equals(method)?"application/octet-stream":"application/json").content(bytes)).andReturn();}
        catch(Exception ex){throw new IllegalStateException(ex);}
    });}
    private String body(MvcResult result){try{return result.getResponse().getContentAsString();}catch(Exception ex){throw new IllegalStateException(ex);}}
}
