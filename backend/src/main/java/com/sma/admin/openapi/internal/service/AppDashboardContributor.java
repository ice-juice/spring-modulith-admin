package com.sma.admin.openapi.internal.service;
import java.util.List;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.openapi.internal.persistence.*;
@Component
public class AppDashboardContributor implements DashboardContributor {
 private final ApplicationMapper apps;public AppDashboardContributor(ApplicationMapper apps){this.apps=apps;}
 public Contribution contribute(){var actor=CurrentUser.require();if(!actor.superAdmin()&&!actor.permissions().contains("openapi:app:view"))return new Contribution(List.of(),List.of());
  return new Contribution(List.of(new Metric("openapi.apps","openapi.dashboard.apps",apps.selectCount(new LambdaQueryWrapper<ApplicationEntity>().eq(ApplicationEntity::getPortal,actor.portal())),"/openapi/apps")),List.of());}
}
