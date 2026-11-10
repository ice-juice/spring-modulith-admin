package com.sma.admin.common.internal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.*;
import com.sma.admin.common.OperationAuditEvent;
import com.sma.admin.common.security.*;
import com.sma.admin.common.internal.service.OperationAuditRecorder;

@Component
class OperationAuditInterceptor implements HandlerInterceptor {
 static final String CONTEXT="sma.audit.context",CODE="sma.audit.code",ERROR="sma.audit.error",ID="sma.audit.id",TYPE="sma.audit.type-id";
 private static final Logger log=LoggerFactory.getLogger(OperationAuditInterceptor.class);
 private record Context(SmaPrincipal user,String action,String clientPath,long started){}
 private final OperationAuditRecorder recorder;
 OperationAuditInterceptor(OperationAuditRecorder recorder){this.recorder=recorder;}
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
  if(Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())||!(handler instanceof HandlerMethod method))return true;
  var permission=AnnotatedElementUtils.findMergedAnnotation(method.getMethod(),RequiresPermission.class);
  if(permission==null)permission=AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(),RequiresPermission.class);
  var custom=AnnotatedElementUtils.findMergedAnnotation(method.getMethod(),AuditAction.class);
  if(permission==null&&custom==null)return true;
  var user=CurrentUser.optional();if(user.isEmpty())return true;
  request.setAttribute(CONTEXT,new Context(user.get(),custom==null?permission.value():custom.value(),custom==null?"":custom.path(),System.nanoTime()));
  Object variables=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
  if(variables instanceof Map<?,?> map&&map.get("id")!=null)request.setAttribute(ID,bound(map.get("id").toString(),128));
  return true;
 }
 @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception error){
  if(!(request.getAttribute(CONTEXT) instanceof Context context))return;
  String path=request.getRequestURI().substring(request.getContextPath().length());
  String objectId=(String)request.getAttribute(ID);
  String client=context.clientPath();
  if(client.isBlank()){
   String[] pieces=path.split("/");
   client=pieces.length>=4?"/"+pieces[2]+"/"+pieces[3]:"/";
  }
  if(objectId!=null)client+=(client.contains("?")?"&":"?")+"focus="+java.net.URLEncoder.encode(objectId,java.nio.charset.StandardCharsets.UTF_8);
  String type=(String)request.getAttribute(TYPE);
  if(type!=null)client+=(client.contains("?")?"&":"?")+"typeId="+java.net.URLEncoder.encode(type,java.nio.charset.StandardCharsets.UTF_8);
  int code=request.getAttribute(CODE) instanceof Integer value?value:response.getStatus();
  if(code>=200&&code<300)code=0;
  String[] action=context.action().split(":",2);
  String messageKey=action[0]+".audit."+action[1].replace(':','.');
  var user=context.user();
  var event=new OperationAuditEvent(UUID.randomUUID().toString(),user.tenantId(),user.userId(),user.departmentId(),user.username(),user.portal(),context.action(),messageKey,
   objectId,bound(client,512),bound(path,512),code,(String)request.getAttribute(ERROR),TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-context.started()),
   request.getRemoteAddr(),bound(request.getHeader("User-Agent"),512),Instant.now());
  try{recorder.record(event);}catch(RuntimeException failure){log.error("Could not persist operation audit event {}",event.eventId(),failure);}
 }
 private String bound(String value,int max){return value==null?null:value.substring(0,Math.min(value.length(),max));}
}
