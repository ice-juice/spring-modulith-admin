package com.sma.admin.common.internal;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import com.sma.admin.common.ApiResponse;

@ControllerAdvice
class OperationAuditResponseAdvice implements ResponseBodyAdvice<Object> {
 @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter){return true;}
 @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType type,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){
  if(request instanceof ServletServerHttpRequest servlet&&body instanceof ApiResponse<?> api){
   var http=servlet.getServletRequest();http.setAttribute(OperationAuditInterceptor.CODE,api.code());http.setAttribute(OperationAuditInterceptor.ERROR,api.error());
   Object data=api.data();
   if(data!=null&&data.getClass().isRecord())for(var component:data.getClass().getRecordComponents()){
    if(!component.getName().equals("id")&&!component.getName().equals("typeId"))continue;
    try{Object value=component.getAccessor().invoke(data);if(value!=null)http.setAttribute(component.getName().equals("id")?OperationAuditInterceptor.ID:OperationAuditInterceptor.TYPE,value.toString());}
    catch(ReflectiveOperationException ignored){/* Audit metadata never changes the response. */}
   }
  }
  return body;
 }
}
