package com.sma.admin.auth;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.auth.internal.persistence.*;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
@IntegrationTest
class SessionMaintenanceTests {
 @Autowired SessionMaintenance maintenance;@Autowired SessionMapper sessions;
 @Test void expiryCleanupOnlyDeletesExpiredRowsOfItsTenant(){
  long a=Users.as(Users.principal(101,299,DataAccess.unrestricted()),()->{var row=create(Instant.now().minusSeconds(1));create(Instant.now().plusSeconds(3600));return row.getId();});
  long b=Users.as(Users.principal(101,300,DataAccess.unrestricted()),()->create(Instant.now().minusSeconds(1)).getId());
  assertThat(maintenance.cleanTenant(299)).isEqualTo(1);
  Users.as(Users.principal(101,299,DataAccess.unrestricted()),()->{assertThat(sessions.selectById(a)).isNull();assertThat(sessions.selectCount(null)).isEqualTo(1);return null;});
  Users.as(Users.principal(101,300,DataAccess.unrestricted()),()->{assertThat(sessions.selectById(b)).isNotNull();return null;});
 }
 private SessionEntity create(Instant expiry){var row=new SessionEntity();row.setUserId(101L);row.setPortal("admin");row.setTokenHash(java.util.UUID.randomUUID().toString().replace("-","")+java.util.UUID.randomUUID().toString().replace("-",""));row.setCredentialVersion(0L);row.setExpiresAt(expiry);row.setLastSeenAt(Instant.now());row.setLocked(false);sessions.insert(row);return row;}
}
