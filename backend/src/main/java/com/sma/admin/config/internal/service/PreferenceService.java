package com.sma.admin.config.internal.service;
import java.time.*;
import jakarta.validation.constraints.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.config.*;
import com.sma.admin.config.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class PreferenceService implements PreferenceQuery {
 private final PreferenceMapper mapper;private final ParameterQuery settings;private final WriteLockMapper lock;private final ApplicationEventPublisher events;
 public PreferenceService(PreferenceMapper mapper,ParameterQuery settings,WriteLockMapper lock,ApplicationEventPublisher events){this.mapper=mapper;this.settings=settings;this.lock=lock;this.events=events;}
 public record Input(@NotBlank @Pattern(regexp="zh-CN|en-US") String locale,@NotBlank @Size(max=64) String timeZone,
  @NotBlank @Pattern(regexp="light|dark|system") String theme,@NotNull Boolean tabsEnabled,@NotNull Boolean keepTabsAfterReload){}
 @Override public PreferenceView get(long userId,String defaultLocale,String defaultTimeZone){
  var row=mapper.selectOne(new LambdaQueryWrapper<PreferenceEntity>().eq(PreferenceEntity::getUserId,userId));
  String tenantZone=settings.stringValue("system.timeZone","");
  return new PreferenceView(row==null?defaultLocale:row.getLocale(),row==null?defaultTimeZone:row.getTimeZone(),tenantZone.isBlank()?null:tenantZone,
   row==null?settings.stringValue("ui.theme","light"):row.getTheme(),row==null?settings.booleanValue("ui.tabsEnabled",true):row.getTabsEnabled(),
   row==null?settings.booleanValue("ui.keepTabsAfterReload",false):row.getKeepTabsAfterReload());
 }
 @Transactional public PreferenceView save(Input input){
  lock.lock(CurrentUser.tenantId());long userId=CurrentUser.require().userId();
  try{ZoneId.of(input.timeZone());}catch(DateTimeException ex){throw new BizException(CommonErrorCode.BAD_REQUEST);}
  var row=mapper.selectOne(new LambdaQueryWrapper<PreferenceEntity>().eq(PreferenceEntity::getUserId,userId));boolean create=row==null;
  if(create){row=new PreferenceEntity();row.setUserId(userId);}
  row.setLocale(input.locale());row.setTimeZone(input.timeZone());row.setTheme(input.theme());row.setTabsEnabled(input.tabsEnabled());row.setKeepTabsAfterReload(input.keepTabsAfterReload());
  if(create)mapper.insert(row);else mapper.updateById(row);
  events.publishEvent(new ConfigurationChangedEvent(CurrentUser.tenantId(),"preference",Long.toString(userId)));return get(userId,null,null);
 }
 @Transactional public void reset(){
  lock.lock(CurrentUser.tenantId());mapper.delete(new LambdaQueryWrapper<PreferenceEntity>().eq(PreferenceEntity::getUserId,CurrentUser.require().userId()));
 }
}
