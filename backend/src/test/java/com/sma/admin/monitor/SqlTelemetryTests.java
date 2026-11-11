package com.sma.admin.monitor;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.common.*;
import com.sma.admin.common.persistence.DatabaseTelemetry;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.service.PostService;
import com.sma.admin.support.*;
@IntegrationTest
class SqlTelemetryTests {
 @Autowired DatabaseTelemetry telemetry;@Autowired PostService posts;
 @Test void realJdbcCallsAndFailuresAreMeasuredWithoutValuesAndResetIsTenantScoped(){
  Users.as(Users.principal(101,297,DataAccess.unrestricted()),()->{
   posts.save(null,new PostService.Input("Do not expose this title","metric",0,true,null));
   assertThatThrownBy(()->posts.save(null,new PostService.Input("Do not expose this title","metric",0,true,null))).isInstanceOf(BizException.class);
   var row=telemetry.statements().stream().filter(statement->statement.id().endsWith("PostMapper.insert")).findFirst().orElseThrow();
   assertThat(row.calls()).isEqualTo(2);assertThat(row.errors()).isEqualTo(1);assertThat(row.rows()).isEqualTo(1);assertThat(row.maxMillis()).isGreaterThanOrEqualTo(0);assertThat(row.toString()).doesNotContain("Do not expose this title");assertThat(row.resourceId()).isEqualTo("platform");assertThat(row.resourceRevision()).isEqualTo(1);return null;
  });
  Users.as(Users.principal(101,298,DataAccess.unrestricted()),()->{assertThat(telemetry.statements()).isEmpty();posts.options();assertThat(telemetry.statements()).isNotEmpty();telemetry.reset();assertThat(telemetry.statements()).isEmpty();return null;});
  Users.as(Users.principal(101,297,DataAccess.unrestricted()),()->{assertThat(telemetry.statements()).isNotEmpty();telemetry.reset();assertThat(telemetry.statements()).isEmpty();return null;});
 }
}
