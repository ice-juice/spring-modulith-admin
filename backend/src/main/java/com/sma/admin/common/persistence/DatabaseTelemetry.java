package com.sma.admin.common.persistence;
import java.time.Instant;
import java.util.List;
/** Tenant-scoped JDBC execution statistics. Statement identifiers contain no SQL text or parameter values. */
public interface DatabaseTelemetry {
 record Statement(String id,String command,long calls,long errors,long rows,long slowCalls,double averageMillis,double maxMillis,Instant lastAt,
                  String resourceId,long resourceRevision){
  public Statement(String id,String command,long calls,long errors,long rows,long slowCalls,double averageMillis,double maxMillis,Instant lastAt){
   this(id,command,calls,errors,rows,slowCalls,averageMillis,maxMillis,lastAt,"unknown",0);
  }
 }
 List<Statement> statements();
 void reset();
}
