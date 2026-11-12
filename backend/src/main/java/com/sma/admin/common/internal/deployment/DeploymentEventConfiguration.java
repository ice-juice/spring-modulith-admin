package com.sma.admin.common.internal.deployment;

import java.util.function.Supplier;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.modulith.events.ApplicationModuleListener;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.deployment.DeploymentWork;

/** Wrap the actual asynchronous invocation, including Modulith's completion/failure persistence. */
@Configuration(proxyBeanMethods=false)
class DeploymentEventConfiguration {
    private final ThreadLocal<Attempt> current=new ThreadLocal<>();

    /** Explicit qualifier only; do not replace Boot's default executor or its unrelated context propagation. */
    @Bean(name="deploymentEventExecutor",defaultCandidate=false)
    org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor deploymentEventExecutor(){
        var executor=new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);executor.setMaxPoolSize(4);executor.setQueueCapacity(128);
        var threads=Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name("sma-events-",0).factory();
        executor.setThreadFactory(action->{
            var thread=threads.newThread(action);
            // Disabling inherited locals also drops the Boot launcher's context loader on Java 21.
            // Use the host's fixed loader, never one supplied by the submitting request/plugin.
            thread.setContextClassLoader(DeploymentEventConfiguration.class.getClassLoader());
            return thread;
        });
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Bean @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Advisor deploymentEventLifetimeAdvisor(ObjectProvider<DeploymentWork> work){return lifetime(work::getObject);}

    @Bean @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Advisor deploymentEventAdmissionAdvisor(){return admission();}

    Advisor lifetime(Supplier<DeploymentWork> work){
        var advisor=new DefaultPointcutAdvisor(pointcut(),(org.aopalliance.intercept.MethodInterceptor)call->{
            var previous=current.get();var attempt=new Attempt();
            // Carry refusal through the framework interceptor so it marks FAILED, never COMPLETED.
            try{
                DeploymentEvidence.requireHost();
                attempt.permit=work.get().tryStart().orElse(null);
            }catch(RuntimeException unavailable){/* Fixed rejection below; do not expose identity/configuration. */}
            current.set(attempt);
            try{return call.proceed();}
            finally{
                if(previous==null)current.remove();else current.set(previous);
                if(attempt.permit!=null)attempt.permit.close();
            }
        });
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE+5);
        return advisor;
    }

    Advisor admission(){
        var advisor=new DefaultPointcutAdvisor(pointcut(),(org.aopalliance.intercept.MethodInterceptor)call->{
            var attempt=current.get();
            if(attempt==null||attempt.permit==null)throw new EventDeferred();
            // Refuse unsupported async method bodies rather than closing a permit on Future creation.
            if(call.getMethod().getReturnType()!=void.class||call.getArguments().length!=1)
                throw new IllegalStateException("Deployment event listener requires one argument and void completion");
            DeploymentEvidence.requireHost();
            return call.proceed();
        });
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE+20);
        return advisor;
    }

    private static AnnotationMatchingPointcut pointcut(){return new AnnotationMatchingPointcut(null,ApplicationModuleListener.class,true);}
    private static final class Attempt { DeploymentWork.Permit permit; }
    static final class EventDeferred extends IllegalStateException {
        EventDeferred(){super("Deployment event consumption deferred");}
    }
}
