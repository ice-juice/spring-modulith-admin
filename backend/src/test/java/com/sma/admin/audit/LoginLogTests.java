package com.sma.admin.audit;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sma.admin.auth.internal.service.LoginEvents;
import com.sma.admin.audit.internal.service.*;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
@IntegrationTest
class LoginLogTests {
 @Autowired LoginEvents events;@Autowired LoginLogService service;@Autowired IpLocationService locations;
 @Autowired LoginLogListener listener;
 @Autowired com.sma.admin.audit.internal.persistence.LoginHistoryMapper history;
 @Test void offlineIpv4Ipv6AndLocalAddressesAreResolvedWithoutNetwork(){
  assertThat(locations.locate("1.2.3.4").countryCode()).isEqualTo("AU");
  assertThat(locations.locate("240e:3b7:3272:d8d0:db09:c067:8d59:539e").countryCode()).isEqualTo("CN");
  assertThat(locations.locate("127.0.0.1").local()).isTrue();
  assertThat(locations.locate("not-an-ip").resolved()).isFalse();
 }
 @Test void durableLoginEventsReachTheCorrectTenant(){
  Users.as(Users.principal(101,201,DataAccess.unrestricted()),()->{
   events.record(null,"audit-tester","admin","PASSWORD_FAILURE","1.2.3.4","Mozilla/5.0 Windows Chrome/100");
   org.awaitility.Awaitility.await().pollInSameThread().atMost(Duration.ofSeconds(8)).untilAsserted(()->{
    var page=service.page(new PageQuery(1,20,null,null),"audit-tester",null,null,null);
    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items().getFirst().country()).isEqualTo("Australia");
    assertThat(page.items().getFirst().browser()).isEqualTo("Chrome");
   });
   Users.as(Users.principal(202,202,DataAccess.unrestricted()),()->{assertThat(service.page(new PageQuery(1,20,null,null),"audit-tester",null,null,null).total()).isZero();return null;});
   return null;
  });
 }
 @Test void eventReplayIsIdempotentAndCountryChangesAreMarked(){
  var first=new com.sma.admin.auth.LoginEvent(UUID.randomUUID().toString(),203,9999L,null,"geo-tester","admin","SUCCESS","1.2.3.4","test",Instant.now().minusSeconds(10));
  var second=new com.sma.admin.auth.LoginEvent(UUID.randomUUID().toString(),203,9999L,null,"geo-tester","admin","SUCCESS","113.92.157.29","test",Instant.now());
  // Exercise deterministic replay ordering directly; the prior test covers actual asynchronous publication.
  var replay=new LoginLogListener(history,locations);
  replay.on(first);replay.on(second);replay.on(second);
  Users.as(Users.principal(101,203,DataAccess.unrestricted()),()->{
   var page=service.page(new PageQuery(1,20,null,null),"geo-tester",null,null,null);
   assertThat(page.total()).isEqualTo(2);assertThat(page.items().getFirst().unusualLocation()).isTrue();return null;
  });
 }
}
