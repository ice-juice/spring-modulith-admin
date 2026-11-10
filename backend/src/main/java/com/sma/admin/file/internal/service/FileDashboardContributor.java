package com.sma.admin.file.internal.service;
import java.util.List;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.file.internal.persistence.*;
@Component
public class FileDashboardContributor implements DashboardContributor {
 private final FileMapper files;public FileDashboardContributor(FileMapper files){this.files=files;}
 public Contribution contribute(){var actor=CurrentUser.require();if(!actor.superAdmin()&&!actor.permissions().contains("file:file:view"))return new Contribution(List.of(),List.of());
  return new Contribution(List.of(new Metric("file.assets","file.dashboard.assets",files.selectCount(new LambdaQueryWrapper<FileEntity>().eq(FileEntity::getPortal,actor.portal())),"/file/files")),List.of());}
}
