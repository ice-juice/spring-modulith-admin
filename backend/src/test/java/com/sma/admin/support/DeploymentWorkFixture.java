package com.sma.admin.support;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.web.context.request.RequestContextHolder;
import com.sma.admin.common.deployment.DeploymentWork;

/** Controllable unit-test qualification, never a production admission substitute. */
public final class DeploymentWorkFixture implements DeploymentWork {
    public volatile boolean accepting=true;
    public final AtomicInteger active=new AtomicInteger();
    @Override public Optional<Permit> tryStart(){
        if(!accepting)return Optional.empty();
        active.incrementAndGet();var closed=new AtomicBoolean();
        return Optional.of(()->{if(closed.compareAndSet(false,true))active.decrementAndGet();});
    }
    @Override public Snapshot snapshot(){return new Snapshot(accepting,false,active.get());}

    /** MOCK Spring tests bind an HTTP request to the test thread; explicitly model a host call. */
    public static <T>T host(Supplier<T> action){
        var request=RequestContextHolder.getRequestAttributes();RequestContextHolder.resetRequestAttributes();
        try{return action.get();}finally{RequestContextHolder.setRequestAttributes(request);}
    }
    public static void host(Runnable action){host(()->{action.run();return null;});}
}
