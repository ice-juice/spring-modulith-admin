package com.sma.admin.config.internal.service;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.sma.admin.common.security.PlatformTenantQuery;
import com.sma.admin.common.security.TenantScope;
@Component public class BuiltinParameters {
 private final ParameterService service;
 private final PlatformTenantQuery platform;
 public BuiltinParameters(ParameterService service,PlatformTenantQuery platform){this.service=service;this.platform=platform;}
 @EventListener(ApplicationReadyEvent.class)
 public void initialize(){TenantScope.within(platform.platformTenantId(),()->{service.initialize(groups(),specs());return null;});}
 public void resetDefaults(){service.initialize(groups(),specs());service.resetBuiltins(specs());}
 public List<ParameterService.SeedGroup> groups(){return List.of(
  new ParameterService.SeedGroup("basic","系统基础","config.group.basic"),
  new ParameterService.SeedGroup("security","系统安全","config.group.security"),
  new ParameterService.SeedGroup("login","登录页面","config.group.login"),
  new ParameterService.SeedGroup("appearance","界面设置","config.group.appearance"),
  new ParameterService.SeedGroup("legal","备案与版权","config.group.legal")
 );}
 public static List<ParameterService.Seed> specs(){return List.of(
  new ParameterService.Seed("system.departmentEnabled","basic","启用部门","config.param.system.departmentEnabled","BOOLEAN","true",false),
  new ParameterService.Seed("system.postEnabled","basic","启用岗位","config.param.system.postEnabled","BOOLEAN","true",false),
  new ParameterService.Seed("system.extendedProfileEnabled","basic","启用用户扩展信息","config.param.system.extendedProfileEnabled","BOOLEAN","true",false),
  new ParameterService.Seed("auth.sessionTtlSeconds","security","普通登录会话时长（秒）","config.param.auth.sessionTtlSeconds","INTEGER","28800",false),
  new ParameterService.Seed("auth.rememberTtlSeconds","security","保持登录时长（秒）","config.param.auth.rememberTtlSeconds","INTEGER","604800",false),
  new ParameterService.Seed("auth.singleTerminal","security","只允许单终端登录","config.param.auth.singleTerminal","BOOLEAN","false",false),
  new ParameterService.Seed("auth.autoLockSeconds","security","空闲自动锁屏（秒，0关闭）","config.param.auth.autoLockSeconds","INTEGER","0",false),
  new ParameterService.Seed("auth.passwordNoticeDays","security","改密提醒天数（0关闭）","config.param.auth.passwordNoticeDays","INTEGER","0",false),
  new ParameterService.Seed("auth.lockAfterRemember","security","保持登录后进入锁屏","config.param.auth.lockAfterRemember","BOOLEAN","false",false),
  new ParameterService.Seed("login.captchaEnabled","login","启用登录验证码","config.param.login.captchaEnabled","BOOLEAN","true",true),
  new ParameterService.Seed("ui.theme","appearance","默认主题","config.param.ui.theme","STRING","light",false),
  new ParameterService.Seed("ui.tabsEnabled","appearance","启用多选项卡","config.param.ui.tabsEnabled","BOOLEAN","true",false),
  new ParameterService.Seed("ui.keepTabsAfterReload","appearance","刷新后保留选项卡","config.param.ui.keepTabsAfterReload","BOOLEAN","false",false)
 );}
}
