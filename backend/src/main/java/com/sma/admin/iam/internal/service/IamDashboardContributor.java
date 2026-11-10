package com.sma.admin.iam.internal.service;
import java.util.*;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.iam.internal.persistence.*;
@Component
public class IamDashboardContributor implements DashboardContributor {
 private final UserMapper users;private final RoleMapper roles;private final DepartmentMapper departments;
 public IamDashboardContributor(UserMapper users,RoleMapper roles,DepartmentMapper departments){this.users=users;this.roles=roles;this.departments=departments;}
 public Contribution contribute(){var actor=CurrentUser.require();List<Metric> metrics=new ArrayList<>();
  if(actor.superAdmin()||actor.permissions().contains("iam:user:view"))metrics.add(new Metric("iam.users","iam.dashboard.users",users.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getPortal,actor.portal())),"/iam/users"));
  if(actor.superAdmin()||actor.permissions().contains("iam:role:view"))metrics.add(new Metric("iam.roles","iam.dashboard.roles",roles.selectCount(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getPortal,actor.portal())),"/iam/roles"));
  if(actor.superAdmin()||actor.permissions().contains("iam:department:view"))metrics.add(new Metric("iam.departments","iam.dashboard.departments",departments.selectCount(null),"/iam/departments"));
  return new Contribution(metrics,List.of());
 }
}
