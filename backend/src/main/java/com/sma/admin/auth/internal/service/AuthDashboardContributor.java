package com.sma.admin.auth.internal.service;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.auth.internal.persistence.*;
@Component
public class AuthDashboardContributor implements DashboardContributor {
 private final SessionMapper sessions;public AuthDashboardContributor(SessionMapper sessions){this.sessions=sessions;}
 public Contribution contribute(){var actor=CurrentUser.require();if(!actor.superAdmin()&&!actor.permissions().contains("auth:session:view"))return new Contribution(List.of(),List.of());
  return new Contribution(List.of(new Metric("auth.sessions","auth.dashboard.sessions",sessions.selectCount(new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getPortal,actor.portal()).gt(SessionEntity::getExpiresAt,Instant.now())),"/auth/sessions")),List.of());}
}
