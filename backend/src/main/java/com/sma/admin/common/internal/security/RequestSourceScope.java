package com.sma.admin.common.internal.security;

import java.util.Optional;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.RequestSource;
import com.sma.admin.common.security.RequestSourceQuery;
import com.sma.admin.common.security.SecuritySignal;

/** Binding stays internal; consumers can only read a snapshot. Not inherited by workers. */
@Component
public class RequestSourceScope implements RequestSourceQuery {
    private final ThreadLocal<Binding> local=new ThreadLocal<>();
    @Override public Optional<RequestSource> current(){return Optional.ofNullable(local.get()).map(value->value.source);}
    Optional<java.util.UUID> correlationId(){return Optional.ofNullable(local.get()).map(value->value.correlationId);}
    SecuritySignal.Http http(){var binding=local.get();return binding==null?null:binding.http;}
    void response(Integer status){var b=local.get();if(b!=null&&b.http!=null)b.http=new SecuritySignal.Http(b.http.method(),b.http.route(),b.http.template(),status);}
    void handler(boolean resource){var b=local.get();if(b!=null){b.handlerSeen=true;b.resourceHandler=resource;
        if(b.http!=null)b.http=new SecuritySignal.Http(b.http.method(),SecuritySignal.Route.UNRESOLVED,null,b.http.status());}}
    void matched(String template){var b=local.get();if(b!=null&&b.http!=null)b.http=new SecuritySignal.Http(b.http.method(),SecuritySignal.Route.MATCHED,template,b.http.status());}
    boolean unmapped(boolean noHandler){
        var b=local.get();if(b==null||b.http==null||(noHandler?b.handlerSeen:!b.resourceHandler))return false;
        b.http=new SecuritySignal.Http(b.http.method(),SecuritySignal.Route.UNMAPPED,null,b.http.status());return true;
    }
    boolean claimRejection(){var binding=local.get();if(binding==null||binding.rejectionAttempted)return false;binding.rejectionAttempted=true;return true;}
    Binding open(RequestSource source){return open(source,null);}
    Binding open(RequestSource source,String method){
        if(local.get()!=null)throw new IllegalStateException("Nested source binding");
        var binding=new Binding(source,method);local.set(binding);return binding;
    }
    final class Binding implements AutoCloseable {
        private final RequestSource source;
        private final java.util.UUID correlationId=java.util.UUID.randomUUID();
        private final Thread owner=Thread.currentThread();
        private boolean closed;
        private boolean rejectionAttempted;
        private SecuritySignal.Http http;
        private boolean handlerSeen,resourceHandler;
        private Binding(RequestSource source,String method){
            this.source=java.util.Objects.requireNonNull(source);
            if(method!=null){SecuritySignal.Method parsed;
                try{parsed=SecuritySignal.Method.valueOf(method);}catch(IllegalArgumentException unknown){parsed=SecuritySignal.Method.OTHER;}
                http=new SecuritySignal.Http(parsed,SecuritySignal.Route.UNRESOLVED,null,null);
            }
        }
        @Override public void close(){
            if(closed)return;
            if(Thread.currentThread()!=owner || local.get()!=this)throw new IllegalStateException("Source binding ownership mismatch");
            local.remove();closed=true;
        }
    }
}
