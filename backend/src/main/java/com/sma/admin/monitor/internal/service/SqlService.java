package com.sma.admin.monitor.internal.service;
import java.util.*;
import org.springframework.stereotype.Service;
import com.sma.admin.common.*;
import com.sma.admin.common.persistence.DatabaseTelemetry;
@Service
public class SqlService {
 private final DatabaseTelemetry telemetry;public SqlService(DatabaseTelemetry telemetry){this.telemetry=telemetry;}
 public PageResult<DatabaseTelemetry.Statement> page(PageQuery page,String keyword){var paging=page.toPage(Map.of("calls","calls","errors","errors","averageMillis","average_millis","maxMillis","max_millis"));
  Comparator<DatabaseTelemetry.Statement> sort=switch(page.sort()==null?"maxMillis":page.sort()){case "calls"->Comparator.comparingLong(DatabaseTelemetry.Statement::calls);case "errors"->Comparator.comparingLong(DatabaseTelemetry.Statement::errors);case "averageMillis"->Comparator.comparingDouble(DatabaseTelemetry.Statement::averageMillis);case "maxMillis"->Comparator.comparingDouble(DatabaseTelemetry.Statement::maxMillis);default->throw new BizException(CommonErrorCode.BAD_REQUEST);};
  if(!"asc".equals(page.direction()))sort=sort.reversed();String filter=keyword==null?"":keyword.toLowerCase(Locale.ROOT);
  var rows=telemetry.statements().stream().filter(row->row.id().toLowerCase(Locale.ROOT).contains(filter)).sorted(sort).toList();
  return new PageResult<>(rows.stream().skip((paging.getCurrent()-1)*paging.getSize()).limit(paging.getSize()).toList(),rows.size(),paging.getCurrent(),paging.getSize());
 }
 public void reset(){telemetry.reset();}
}
