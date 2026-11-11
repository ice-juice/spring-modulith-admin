package com.sma.admin.file.internal.web;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.service.UploadTaskProperties;
import com.sma.admin.support.Users;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class UploadTaskRequestFilterTests {
    private final UploadTaskRequestFilter filter=new UploadTaskRequestFilter((request,response,error)->response.setStatus(error.status()),
            new UploadTaskProperties(null,null,null,null,null,null,null,null,null,null));
    private MockHttpServletRequest request(String method,long length,byte[] content){
        var request=new MockHttpServletRequest(method,"/api/file/upload-tasks"+(method.equals("PUT")?"/task/parts/1":"")){
            @Override public long getContentLengthLong(){return length;}
        };
        request.setContentType(method.equals("PUT")?"application/octet-stream":"application/json");request.setContent(content);
        if(method.equals("PUT")){request.addHeader("X-Upload-Size","3");request.addHeader("X-Upload-SHA256","a".repeat(64));}return request;
    }
    private void admin(Throwing action){Users.as(Users.principal(1,1,DataAccess.none()),()->{try{action.run();return null;}catch(Exception failure){throw new RuntimeException(failure);}});}
    @FunctionalInterface interface Throwing {void run()throws Exception;}
    @Test void authenticationPrecedesBodyConsumptionAndOtherRoutesAreUnaffected()throws Exception{
        var request=request("POST",-1,new byte[0]);var entered=new AtomicBoolean();var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->entered.set(true));assertThat(response.getStatus()).isEqualTo(401);assertThat(entered).isFalse();
        request.setRequestURI("/api/file/uploads/lookup");filter.doFilter(request,new MockHttpServletResponse(),(req,res)->entered.set(true));assertThat(entered).isTrue();
    }
    @Test void actualJsonLimitDoesNotTrustMissingOrFalseContentLength(){admin(()->{
        for(long length:List.of(-1L,0L,65537L)){
            var response=new MockHttpServletResponse();filter.doFilter(request("POST",length,new byte[65537]),response,(req,res)->fail("Oversized JSON reached MVC"));assertThat(response.getStatus()).isEqualTo(413);
        }
        byte[] original="{ \"label\": \"原始内容\" }".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        filter.doFilter(request("POST",-1,original),new MockHttpServletResponse(),(req,res)->assertThat(req.getInputStream().readAllBytes()).containsExactly(original));
    });}
    @Test void binaryStreamRejectsExtraActualBytesAndPreservesExactBody(){admin(()->{
        var response=new MockHttpServletResponse();filter.doFilter(request("PUT",-1,new byte[]{1,2,3,4}),response,(req,res)->
            assertThatThrownBy(()->req.getInputStream().readAllBytes()).isInstanceOf(BizException.class));
        filter.doFilter(request("PUT",3,new byte[]{1,2,3}),new MockHttpServletResponse(),(req,res)->assertThat(req.getInputStream().readAllBytes()).containsExactly(1,2,3));
    });}
    @Test void rejectsAmbiguousOrEncodedHeadersAndLengthBeforeReading(){admin(()->{
        var wrongLength=request("PUT",4,new byte[]{1,2,3,4});var duplicate=request("PUT",3,new byte[]{1,2,3});duplicate.addHeader("X-Upload-Size","3");
        var encoded=request("PUT",3,new byte[]{1,2,3});encoded.addHeader("Content-Encoding","gzip");
        var tooLarge=request("PUT",-1,new byte[0]);tooLarge.removeHeader("X-Upload-Size");tooLarge.addHeader("X-Upload-Size","16777217");
        var duplicateKey=request("POST",-1,new byte[0]);duplicateKey.addHeader("Idempotency-Key","a".repeat(16));duplicateKey.addHeader("Idempotency-Key","b".repeat(16));
        for(var input:List.of(wrongLength,duplicate,encoded,tooLarge,duplicateKey)){
            var response=new MockHttpServletResponse();filter.doFilter(input,response,(req,res)->fail("Malformed request reached MVC"));assertThat(response.getStatus()).isIn(400,413,415);
        }
    });}
    @Test void nodeConcurrencyIsBoundedAndSlotsReturnAfterConsumerFailure(){admin(()->{
        occupy(0);assertThatThrownBy(()->filter.doFilter(request("GET",0,new byte[0]),new MockHttpServletResponse(),(req,res)->{throw new IllegalStateException("consumer");})).isInstanceOf(IllegalStateException.class);
        occupy(0);
    });}
    private void occupy(int depth)throws Exception{
        var response=new MockHttpServletResponse();filter.doFilter(request("GET",0,new byte[0]),response,(req,res)->{
            if(depth==16)fail("Concurrency ceiling exceeded");try{occupy(depth+1);}catch(Exception failure){throw new RuntimeException(failure);}
        });assertThat(response.getStatus()).isEqualTo(depth==16?503:200);
    }
}
