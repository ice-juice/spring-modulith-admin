package com.sma.admin.common.internal;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.common.security.PermitAnonymous;
import com.sma.admin.common.security.RequiresPermission;

@Configuration(proxyBeanMethods = false)
class RequestGovernanceConfiguration {
    @Bean
    @org.springframework.context.annotation.Role(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE)
    Advisor requestGovernanceAdvisor(ObjectProvider<RequestExecution> executions, ObjectProvider<RequestAdmission> admissions, ObjectProvider<JsonMapper> serializers,
            ObjectProvider<com.sma.admin.common.internal.security.RequestInputFingerprint> inputs,
            ObjectProvider<com.sma.admin.common.internal.security.RequestGovernanceProperties> limits) {
        MethodInterceptor interceptor = invocation -> {
            Method method = invocation.getMethod();
            if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null
                    || !(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return invocation.proceed();
            Object target = invocation.getThis();
            var controllerPolicy = AnnotatedElementUtils.findMergedAnnotation(target.getClass(), GovernedApi.class);
            if (controllerPolicy == null) return invocation.proceed();
            var methodPolicy = AnnotatedElementUtils.findMergedAnnotation(method, GovernedApi.class);
            var marker = methodPolicy == null ? controllerPolicy : methodPolicy;
            if (marker.mode() != GovernedApi.Mode.LOCAL && marker.reason().isBlank())
                throw new IllegalStateException("Non-local request policy requires a reviewed reason");
            if (!(target instanceof RequestAuthorizer authorizer)) throw new IllegalStateException("Governed controller must provide module authorization");
            if (AnnotatedElementUtils.hasAnnotation(method, PermitAnonymous.class)) return invocation.proceed();
            var execution = executions.getObject();
            var json = serializers.getObject();
            var user = CurrentUser.require();
            HttpServletRequest request = attributes.getRequest();
            Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (route == null) throw new IllegalStateException("Governed request requires a resolved MVC mapping");
            // Bind to the HTTP contract, not a Java method/class name that may be refactored.
            String operation = marker.group() + "." + digest((request.getMethod() + " " + route).getBytes(StandardCharsets.UTF_8)).substring(0, 24);
            var permission = AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class);
            if (permission == null) permission = AnnotatedElementUtils.findMergedAnnotation(target.getClass(), RequiresPermission.class);
            String permissionKey = permission == null ? "" : permission.value();
            if (!permissionKey.isEmpty() && !user.permissions().contains(permissionKey)) throw new AccessDeniedException("Operation permission required");
            boolean safeMethod = Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
            boolean read = safeMethod || marker.mode() == GovernedApi.Mode.QUERY
                    || marker.mode() == GovernedApi.Mode.NATIVE && authorizer.readOnlyRequest(method.getName(),invocation.getArguments().clone());
            if (attributes.getResponse() != null) attributes.getResponse().setHeader("X-Request-Policy", read ? "QUERY" : marker.mode().name());
            execution.claimBudget(operation + (read ? ".read" : ".write"), read ? marker.readLimit() : marker.writeLimit(), Duration.ofMinutes(1));
            execution.claimTenantBudget(read ? "http.read" : "http.write", read ? 6000 : 1500, Duration.ofMinutes(1));
            if (safeMethod) return invocation.proceed();
            Map<String, String> variables = variables(request, method, invocation.getArguments());
            if (marker.mode() == GovernedApi.Mode.NATIVE) {
                var extra=authorizer.requestResources(method.getName(),invocation.getArguments().clone());
                if(extra==null||extra.size()>4)throw new IllegalStateException("Invalid native request scope");
                var scoped=new TreeMap<>(variables);
                extra.forEach((name,value)->{
                    if(name==null||!name.matches("[a-z][a-z0-9.-]{0,31}")||value==null||value.isBlank()||value.length()>128)
                        throw new IllegalStateException("Invalid native request scope");
                    scoped.put("@"+name,value);
                });
                variables=Collections.unmodifiableMap(scoped);
            }
            final Map<String,String> scopeVariables=variables;
            if (marker.mode() != GovernedApi.Mode.LOCAL) {
                String scope = operation + "\n" + (marker.ownResource() ? "user:" + user.userId() + ":" : "")
                        + (variables.isEmpty() ? "collection" : digest(json.writeValueAsBytes(variables)));
                return admissions.getObject().execute(new RequestExecution.Work<Object>() {
                    @Override public void authorize(){authorizer.authorizeRequest(method.getName(),scopeVariables);}
                    @Override public Object execute(){
                        if (!read) execution.claimBudget("tap." + digest(scope.getBytes(StandardCharsets.UTF_8)).substring(0,40),1,limits.getObject().minimumWriteInterval());
                        return proceed(invocation);
                    }
                });
            }
            String key = request.getHeader("Idempotency-Key");
            if (key == null || !key.matches("[A-Za-z0-9_.:-]{16,128}")) throw new RequestRejected(RequestRejected.Reason.KEY_REQUIRED, 0);
            var body = new ArrayList<Object>();
            var parameters = method.getParameters();
            for (int index = 0; index < parameters.length; index++)
                if (parameters[index].isAnnotationPresent(RequestBody.class)) body.add(invocation.getArguments()[index]);
            var envelope = new TreeMap<String, Object>();
            envelope.put("path", variables); envelope.put("query", new TreeMap<>(request.getParameterMap())); envelope.put("body", body);
            byte[] inputFingerprint = inputs.getObject().digest(envelope);
            String resource = marker.ownResource() ? "user:" + user.userId() + (variables.isEmpty() ? "" : ":" + digest(json.writeValueAsBytes(variables)))
                    : variables.isEmpty() ? "intent:" + key : digest(json.writeValueAsBytes(variables));
            String tapScope = operation + "\n" + (variables.isEmpty() && !marker.ownResource() ? "collection" : resource);
            var command = new RequestCommand(operation, marker.group(), resource, key,
                    inputFingerprint, permissionKey);
            var outcome = execution.executeLocal(command, new RequestExecution.Work<Object>() {
                @Override public void authorize() { authorizer.authorizeRequest(method.getName(), scopeVariables); }
                @Override public Object execute() {
                    // Only the winner pays this budget; a retry with the original
                    // key returns its receipt without consuming another cooldown.
                    execution.claimBudget("tap." + digest(tapScope.getBytes(StandardCharsets.UTF_8)).substring(0, 40),
                            1, limits.getObject().minimumWriteInterval());
                    return proceed(invocation);
                }
            });
            if (attributes.getResponse() != null) attributes.getResponse().setHeader("X-Operation-Id", outcome.receipt().operationId());
            if (outcome.replayed()) throw new RequestStateException(outcome.receipt());
            return outcome.result();
        };
        var advisor = new DefaultPointcutAdvisor(new AnnotationMatchingPointcut(GovernedApi.class, true), interceptor);
        // Spring's PreAuthorize interceptor (200) runs before this advisor.
        advisor.setOrder(300); return advisor;
    }
    private static Object proceed(org.aopalliance.intercept.MethodInvocation invocation) {
        try { return invocation.proceed(); }
        catch (RuntimeException failure) { throw failure; }
        catch (Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Controller invocation failed", failure); }
    }
    private static Map<String, String> variables(HttpServletRequest request, Method method, Object[] arguments) {
        var result = new TreeMap<String, String>();
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> values)
            values.forEach((key, value) -> result.put(String.valueOf(key), String.valueOf(value)));
        var parameters = method.getParameters();
        for (int index=0;index<parameters.length;index++) {
            var attribute=parameters[index].getAnnotation(org.springframework.web.bind.annotation.RequestAttribute.class);
            if(attribute!=null && ("sma.session-id".equals(attribute.value())||"sma.session-id".equals(attribute.name()))
                    && arguments[index] instanceof Number number) result.put("@session",number.toString());
            var path = parameters[index].getAnnotation(org.springframework.web.bind.annotation.PathVariable.class);
            if (path != null && (arguments[index] instanceof Number || arguments[index] instanceof UUID)) {
                String name = !path.value().isBlank() ? path.value() : !path.name().isBlank() ? path.name() : parameters[index].getName();
                result.put(name, arguments[index].toString());
            }
        }
        return Collections.unmodifiableMap(result);
    }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
