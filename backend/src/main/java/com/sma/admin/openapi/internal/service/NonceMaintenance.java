package com.sma.admin.openapi.internal.service;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.*;
import com.sma.admin.openapi.internal.persistence.NonceMapper;
@Component
public class NonceMaintenance {
 private static final Logger log=LoggerFactory.getLogger(NonceMaintenance.class);
 private final NonceMapper nonces;private final ConcurrentHashMap<Long,Instant> tenants=new ConcurrentHashMap<>();
 public NonceMaintenance(NonceMapper nonces){this.nonces=nonces;}
 public void seen(){tenants.put(CurrentUser.tenantId(),Instant.now());}
 @Scheduled(fixedDelay=60000) public void purge(){var now=Instant.now();tenants.forEach((tenant,last)->{
  try{TenantScope.within(tenant,()->nonces.purge(now));if(last.isBefore(now.minusSeconds(3600)))tenants.remove(tenant,last);}
  catch(org.springframework.dao.DataAccessException failure){log.debug("Could not clean expired application nonces for tenant {}",tenant);}
 });}
}
