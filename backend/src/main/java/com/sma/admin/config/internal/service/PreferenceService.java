package com.sma.admin.config.internal.service;
import java.time.*;
import jakarta.validation.constraints.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import com.sma.admin.common.cache.ManagedCache;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.config.*;
import com.sma.admin.config.internal.persistence.*;

@Service @Transactional(readOnly=true)
public class PreferenceService implements PreferenceQuery {
 private final PreferenceMapper mapper;private final ParameterQuery settings;private final WriteLockMapper lock;private final ApplicationEventPublisher events;
 private final ManagedCache cache;
 private final org.springframework.beans.factory.ObjectProvider<AppearancePolicy> appearance;
 public PreferenceService(PreferenceMapper mapper,ParameterQuery settings,WriteLockMapper lock,ApplicationEventPublisher events,ManagedCache cache,org.springframework.beans.factory.ObjectProvider<AppearancePolicy> appearance){this.mapper=mapper;this.settings=settings;this.lock=lock;this.events=events;this.cache=cache;this.appearance=appearance;}
 public record Input(@NotBlank @Pattern(regexp="zh-CN|en-US") String locale,@NotBlank @Size(max=64) String timeZone,
  @NotBlank @Pattern(regexp="light|dark|system") String theme,@NotNull Boolean tabsEnabled,@NotNull Boolean keepTabsAfterReload,
  @Pattern(regexp="iris|celadon|amber") String skin){
  public Input(String locale,String timeZone,String theme,Boolean tabsEnabled,Boolean keepTabsAfterReload){this(locale,timeZone,theme,tabsEnabled,keepTabsAfterReload,null);}
 }
 public record RawPreference(String locale,String timeZone,String theme,boolean tabsEnabled,boolean keepTabsAfterReload,String skin){}
 private RawPreference load(long userId){
  var row=mapper.selectOne(new LambdaQueryWrapper<PreferenceEntity>().eq(PreferenceEntity::getUserId,userId));
  return row==null?null:new RawPreference(row.getLocale(),row.getTimeZone(),row.getTheme(),row.getTabsEnabled(),row.getKeepTabsAfterReload(),row.getSkin());
 }
 @Override @Transactional(propagation=Propagation.SUPPORTS,readOnly=true) public PreferenceView get(long userId,String defaultLocale,String defaultTimeZone){
  boolean own=CurrentUser.optional().filter(user->!user.machine()&&user.userId()==userId).isPresent();
  var row=own?cache.get(ConfigCacheCatalog.PREFERENCES,"raw",RawPreference.class,()->load(userId)):load(userId);
  String tenantZone=settings.stringValue("system.timeZone","");
  var policy=policy();
  return new PreferenceView(row==null?defaultLocale:row.locale(),row==null?defaultTimeZone:row.timeZone(),tenantZone.isBlank()?null:tenantZone,
   row==null?settings.stringValue("ui.theme","light"):row.theme(),row==null?settings.booleanValue("ui.tabsEnabled",true):row.tabsEnabled(),
   row==null?settings.booleanValue("ui.keepTabsAfterReload",false):row.keepTabsAfterReload(),row==null||!policy.allowPersonalSkin()?policy.defaultSkin():row.skin());
 }
 @Transactional public PreferenceView save(Input input){
  lock.lock(CurrentUser.tenantId());long userId=CurrentUser.require().userId();
  try{ZoneId.of(input.timeZone());}catch(DateTimeException ex){throw new BizException(CommonErrorCode.BAD_REQUEST);}
  if(input.skin()!=null&&!java.util.Set.of("iris","celadon","amber").contains(input.skin()))throw new BizException(CommonErrorCode.BAD_REQUEST);
  var row=mapper.selectOne(new LambdaQueryWrapper<PreferenceEntity>().eq(PreferenceEntity::getUserId,userId));boolean create=row==null;
  if(create){row=new PreferenceEntity();row.setUserId(userId);}
  var policy=policy();
  if(input.skin()!=null&&policy.allowPersonalSkin())row.setSkin(input.skin());else if(create)row.setSkin(policy.defaultSkin());
  row.setLocale(input.locale());row.setTimeZone(input.timeZone());row.setTheme(input.theme());row.setTabsEnabled(input.tabsEnabled());row.setKeepTabsAfterReload(input.keepTabsAfterReload());
  if(create)mapper.insert(row);else mapper.updateById(row);
  changed(userId);return get(userId,null,null);
 }
 @Transactional public void reset(){
  lock.lock(CurrentUser.tenantId());mapper.delete(new LambdaQueryWrapper<PreferenceEntity>().eq(PreferenceEntity::getUserId,CurrentUser.require().userId()));
  changed(CurrentUser.require().userId());
 }
 private void changed(long userId){cache.invalidate(ConfigCacheCatalog.PREFERENCES);events.publishEvent(new ConfigurationChangedEvent(CurrentUser.tenantId(),"preference",Long.toString(userId)));}
 private AppearancePolicy.Decision policy(){var provider=appearance.getIfAvailable();return provider==null?AppearancePolicy.Decision.defaults():provider.current();}
}
