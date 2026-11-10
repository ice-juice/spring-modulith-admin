package com.sma.admin.audit.internal.service;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.audit.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class LoginLogService {
 private final LoginLogMapper mapper;
 public LoginLogService(LoginLogMapper mapper){this.mapper=mapper;}
 public record View(Long id,Long userId,String username,String portal,String status,String ip,String browser,String operatingSystem,
  String country,String province,String city,boolean localAddress,boolean ipChanged,boolean unusualLocation,Instant occurredAt){}
 public PageResult<View> page(PageQuery page,String username,String status,Instant from,Instant to){
  var query=new LambdaQueryWrapper<LoginLogEntity>().eq(LoginLogEntity::getPortal,CurrentUser.portal()).orderByDesc(LoginLogEntity::getOccurredAt);
  if(username!=null&&!username.isBlank())query.like(LoginLogEntity::getUsername,username);
  if(status!=null&&!status.isBlank())query.eq(LoginLogEntity::getStatus,status);
  if(from!=null)query.ge(LoginLogEntity::getOccurredAt,from);if(to!=null)query.lt(LoginLogEntity::getOccurredAt,to);
  return PageResult.of(mapper.selectPage(page.toPage(Map.of("occurredAt","occurred_at")),query),row->new View(row.getId(),row.getUserId(),row.getUsername(),row.getPortal(),row.getStatus(),
   row.getIp(),row.getBrowser(),row.getOperatingSystem(),row.getCountry(),row.getProvince(),row.getCity(),row.getLocalAddress(),row.getIpChanged(),row.getUnusualLocation(),row.getOccurredAt()));
 }
}
