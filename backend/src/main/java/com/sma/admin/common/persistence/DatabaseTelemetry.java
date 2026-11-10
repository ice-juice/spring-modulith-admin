package com.sma.admin.common.persistence;
import java.time.Instant;
import java.util.List;
/** Tenant-scoped JDBC execution statistics. Statement identifiers contain no SQL text or parameter values. */
public interface DatabaseTelemetry {
 record Statement(String id,String command,long calls,long errors,long rows,long slowCalls,double averageMillis,double maxMillis,Instant lastAt){}
 List<Statement> statements();
 void reset();
}
