package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.support.CompletionRegisteringAdvisor;
import org.springframework.scheduling.annotation.AsyncAnnotationAdvisor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.sma.admin.support.DeploymentWorkFixture;

class DeploymentEventTests {
    @Test void isolatedHostRetainsItsLoaderOnEventThreads()throws Exception{
        String name=DeploymentEventConfiguration.class.getName();
        var location=DeploymentEventConfiguration.class.getProtectionDomain().getCodeSource().getLocation();
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{location},DeploymentEventConfiguration.class.getClassLoader()){
            @Override protected synchronized Class<?> loadClass(String candidate,boolean resolve)throws ClassNotFoundException{
                if(!candidate.equals(name)&&!candidate.startsWith(name+"$"))return super.loadClass(candidate,resolve);
                var loaded=findLoadedClass(candidate);if(loaded==null)loaded=findClass(candidate);
                if(resolve)resolveClass(loaded);return loaded;
            }
        }){
            var host=loader.loadClass(name);var constructor=host.getDeclaredConstructor();constructor.setAccessible(true);
            var factory=host.getDeclaredMethod("deploymentEventExecutor");factory.setAccessible(true);
            var executor=(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor)factory.invoke(constructor.newInstance());
            executor.initialize();
            try{
                assertThat(executor.submit(()->{
                    var context=Thread.currentThread().getContextClassLoader();
                    assertThat(context).isSameAs(loader);
                    return Class.forName(name,false,context);
                }).get(3,TimeUnit.SECONDS)).isSameAs(host);
            }finally{executor.shutdown();assertThat(executor.getThreadPoolExecutor().awaitTermination(3,TimeUnit.SECONDS)).isTrue();}
        }
    }
    @Test void dedicatedExecutorIsBoundedRejectsOverflowAndDoesNotPropagateRequestIdentity()throws Exception{
        var executor=new DeploymentEventConfiguration().deploymentEventExecutor();executor.initialize();
        var occupied=new CountDownLatch(4);var release=new CountDownLatch(1);var failures=new ConcurrentLinkedQueue<Throwable>();
        var inherited=new InheritableThreadLocal<String>();inherited.set("request");
        var request=new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest());
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(request,true);
        try{
            com.sma.admin.common.security.ActorContext.withinJob(1,"publisher",()->{
                for(int i=0;i<4;i++)executor.execute(()->{
                    try{
                        assertThat(inherited.get()).isNull();
                        assertThat(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).isNull();
                        assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();
                    }catch(Throwable failed){failures.add(failed);}finally{occupied.countDown();}
                    try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Executor not released");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
                });return null;
            });
            assertThat(occupied.await(3,TimeUnit.SECONDS)).isTrue();assertThat(failures).isEmpty();
            for(int i=0;i<128;i++)executor.execute(()->{});
            assertThatThrownBy(()->executor.execute(()->{throw new AssertionError("CallerRuns forbidden");}))
                    .isInstanceOf(org.springframework.core.task.TaskRejectedException.class);
            assertThat(executor.getQueueSize()).isEqualTo(128);
        }finally{release.countDown();executor.shutdown();inherited.remove();org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    }
    public static class Listener {
        Runnable work=()->{};
        @ApplicationModuleListener public void on(String event){work.run();}
    }
    private static class Fixture {
        final DeploymentWorkFixture work=new DeploymentWorkFixture();
        final EventPublicationRegistry registry=mock(EventPublicationRegistry.class);
        final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
        final Listener target=new Listener();final Listener listener;
        Fixture(Executor executor){
            when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            var config=new DeploymentEventConfiguration();var source=new AnnotationTransactionAttributeSource();
            var transactions=new BeanFactoryTransactionAttributeSourceAdvisor();transactions.setTransactionAttributeSource(source);
            var interceptor=new TransactionInterceptor();interceptor.setTransactionManager(manager);interceptor.setTransactionAttributeSource(source);transactions.setAdvice(interceptor);
            var advisors=new ArrayList<Advisor>(List.of(new AsyncAnnotationAdvisor(executor,(ex,method,args)->{}),
                    config.lifetime(()->work),new CompletionRegisteringAdvisor(()->registry),config.admission(),transactions));
            AnnotationAwareOrderComparator.sort(advisors);
            assertThat(advisors.get(0)).isInstanceOf(AsyncAnnotationAdvisor.class);
            assertThat(advisors.get(2)).isInstanceOf(CompletionRegisteringAdvisor.class);
            assertThat(advisors.getLast()).isSameAs(transactions);
            var factory=new ProxyFactory(target);factory.addAdvisors(advisors);listener=(Listener)factory.getProxy();
        }
    }
    @Test void queuedInvocationAcquiresOnlyOnExecutionAndRefusalIsFailedBeforeTransaction(){
        var queue=new LinkedBlockingQueue<Runnable>();var f=new Fixture(queue::add);var calls=new AtomicInteger();f.target.work=calls::incrementAndGet;
        f.listener.on("queued");assertThat(f.work.active).hasValue(0);verifyNoInteractions(f.registry,f.manager);
        f.work.accepting=false;queue.remove().run();assertThat(calls).hasValue(0);verifyNoInteractions(f.manager);
        verify(f.registry).markProcessing(eq("queued"),any());verify(f.registry).markFailed(eq("queued"),any());
        verify(f.registry,never()).markCompleted(any(),any());assertThat(f.work.active).hasValue(0);
        f.work.accepting=true;f.listener.on("retry");queue.remove().run();assertThat(calls).hasValue(1);assertThat(f.work.active).hasValue(0);
        verify(f.registry).markCompleted(eq("retry"),any());
    }
    @Test void permitCoversActualCommitAndFrameworkCompletionEvenAfterDrain(){
        var f=new Fixture(Runnable::run);
        f.target.work=()->{assertThat(f.work.active).hasValue(1);f.work.accepting=false;};
        doAnswer(i->{assertThat(f.work.active).hasValue(1);return null;}).when(f.manager).commit(any());
        doAnswer(i->{assertThat(f.work.active).hasValue(1);return null;}).when(f.registry).markCompleted(any(),any());
        f.listener.on("admitted");verify(f.manager).commit(any());verify(f.registry).markCompleted(eq("admitted"),any());assertThat(f.work.active).hasValue(0);
        f.listener.on("later");verify(f.manager,times(1)).getTransaction(any());verify(f.registry).markFailed(eq("later"),any());
    }
    @Test void rollbackAndFailedCompletionReleaseAfterFrameworkFailureHandling(){
        var f=new Fixture(Runnable::run);f.target.work=()->{throw new IllegalStateException("fixture");};
        doAnswer(i->{assertThat(f.work.active).hasValue(1);return null;}).when(f.manager).rollback(any());
        doAnswer(i->{assertThat(f.work.active).hasValue(1);return null;}).when(f.registry).markFailed(any(),any());
        f.listener.on("failed");verify(f.manager).rollback(any());verify(f.registry).markFailed(eq("failed"),any());assertThat(f.work.active).hasValue(0);
        f.target.work=()->{};doThrow(new IllegalStateException("completion unavailable")).when(f.registry).markCompleted(any(),any());
        f.listener.on("unknown-completion");assertThat(f.work.active).hasValue(0);verify(f.manager).commit(any());
    }
    @Test void requestIdentityAndOuterTransactionFailBeforeListenerTransaction(){
        var f=new Fixture(Runnable::run);var called=new AtomicInteger();f.target.work=called::incrementAndGet;
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
        try{f.listener.on("request");}finally{org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try{f.listener.on("transaction");}finally{org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
        com.sma.admin.common.security.ActorContext.withinJob(1,"job",()->{f.listener.on("job");return null;});
        assertThat(called).hasValue(0);verifyNoInteractions(f.manager);verify(f.registry,times(3)).markFailed(any(),any());assertThat(f.work.active).hasValue(0);
    }
    @Test void nestedInvocationRestoresOuterScopeAndNoThreadLocalLeaks(){
        var f=new Fixture(Runnable::run);var count=new AtomicInteger();
        f.target.work=()->{if(count.incrementAndGet()==1){f.listener.on("nested");assertThat(f.work.active).hasValue(1);}};
        f.listener.on("outer");assertThat(count).hasValue(2);assertThat(f.work.active).hasValue(0);
        f.listener.on("independent");assertThat(count).hasValue(3);assertThat(f.work.active).hasValue(0);
    }
}
