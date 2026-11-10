package com.sma.admin.common.internal;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.sma.admin.common.persistence.DatabaseTelemetry;
import com.sma.admin.common.security.CurrentUser;

@Component
@Intercepts({@Signature(type=StatementHandler.class,method="query",args={Statement.class,ResultHandler.class}),@Signature(type=StatementHandler.class,method="update",args={Statement.class}),@Signature(type=StatementHandler.class,method="queryCursor",args={Statement.class})})
public class SqlTelemetry implements Interceptor,DatabaseTelemetry {
 private record Key(long tenant,String id,String command){}
 private static class Counters{final LongAdder calls=new LongAdder(),errors=new LongAdder(),rows=new LongAdder(),slow=new LongAdder(),nanos=new LongAdder();final AtomicLong max=new AtomicLong();volatile Instant last=Instant.now();}
 private final ConcurrentHashMap<Key,Counters> stats=new ConcurrentHashMap<>();private final long slowNanos;
 public SqlTelemetry(@Value("${sma.persistence.slow-statement-millis:500}")long slowMillis){if(slowMillis<0||slowMillis>3600000)throw new IllegalArgumentException("Invalid slow statement threshold");slowNanos=slowMillis*1000000;}
 @Override public Object intercept(Invocation invocation)throws Throwable{
  var target=PluginUtils.realTarget(invocation.getTarget());var metadata=SystemMetaObject.forObject(target);
  String property=metadata.hasGetter("delegate.mappedStatement")?"delegate.mappedStatement":metadata.hasGetter("mappedStatement")?"mappedStatement":null;
  if(property==null)return invocation.proceed();var statement=(MappedStatement)metadata.getValue(property);String id=statement.getId();
  if(!id.startsWith("com.sma.admin.")||id.contains("BootstrapMapper."))return invocation.proceed();
  var key=new Key(CurrentUser.tenantId(),id,statement.getSqlCommandType().name());
  if(stats.size()>=10000&&!stats.containsKey(key))return invocation.proceed();var counters=stats.computeIfAbsent(key,ignored->new Counters());
  long start=System.nanoTime(),rows=0;boolean failed=true;
  try{Object result=invocation.proceed();failed=false;rows=result instanceof List<?> list?list.size():result instanceof Number count?Math.max(0,count.longValue()):0;return result;}
  finally{long elapsed=System.nanoTime()-start;counters.calls.increment();if(failed)counters.errors.increment();counters.rows.add(rows);if(elapsed>=slowNanos)counters.slow.increment();counters.nanos.add(elapsed);counters.max.accumulateAndGet(elapsed,Math::max);counters.last=Instant.now();}
 }
 @Override public List<DatabaseTelemetry.Statement> statements(){long tenant=CurrentUser.tenantId();return stats.entrySet().stream().filter(entry->entry.getKey().tenant()==tenant).map(entry->{var value=entry.getValue();long calls=value.calls.sum();return new DatabaseTelemetry.Statement(entry.getKey().id(),entry.getKey().command(),calls,value.errors.sum(),value.rows.sum(),value.slow.sum(),calls==0?0:(double)value.nanos.sum()/calls/1000000d,value.max.get()/1000000d,value.last);}).toList();}
 @Override public void reset(){long tenant=CurrentUser.tenantId();stats.keySet().removeIf(key->key.tenant()==tenant);}
}
