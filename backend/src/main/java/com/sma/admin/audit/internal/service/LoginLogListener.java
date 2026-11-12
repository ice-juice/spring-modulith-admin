package com.sma.admin.audit.internal.service;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.auth.LoginEvent;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.audit.internal.persistence.*;

@Component
public class LoginLogListener {
 private final LoginHistoryMapper mapper;private final IpLocationService locations;
 public LoginLogListener(LoginHistoryMapper mapper,IpLocationService locations){this.mapper=mapper;this.locations=locations;}
 @org.springframework.scheduling.annotation.Async("deploymentEventExecutor") @ApplicationModuleListener
 public void on(LoginEvent event){
  TenantScope.within(event.tenantId(),()->{
   var location=locations.locate(event.ip());var device=UserAgentParser.parse(event.userAgent());
   LoginLogEntity previous=null;
   if(event.userId()!=null&&event.status().equals("SUCCESS"))previous=mapper.selectOne(new LambdaQueryWrapper<LoginLogEntity>()
    .eq(LoginLogEntity::getUserId,event.userId()).eq(LoginLogEntity::getStatus,"SUCCESS").lt(LoginLogEntity::getOccurredAt,event.occurredAt()).orderByDesc(LoginLogEntity::getOccurredAt).last("LIMIT 1"));
   var row=new LoginLogEntity();row.setEventId(event.eventId());row.setUserId(event.userId());row.setDeptId(event.departmentId());row.setUsername(event.username());row.setPortal(event.portal());
   row.setStatus(event.status());row.setIp(event.ip());row.setUserAgent(event.userAgent());row.setBrowser(device.browser());row.setOperatingSystem(device.operatingSystem());
   row.setCountry(location.country());row.setProvince(location.province());row.setCity(location.city());row.setCountryCode(location.countryCode());row.setLocalAddress(location.local());
   row.setIpChanged(previous!=null&&!Objects.equals(previous.getIp(),event.ip()));
   row.setUnusualLocation(previous!=null&&previous.getCountryCode()!=null&&location.resolved()
    &&(!Objects.equals(previous.getCountryCode(),location.countryCode())||previous.getProvince()!=null&&location.province()!=null&&!previous.getProvince().equals(location.province())));
   row.setOccurredAt(event.occurredAt());
   try{mapper.insert(row);}catch(DuplicateKeyException duplicate){/* Event replay is idempotent. */}
   return null;
  });
 }
}
