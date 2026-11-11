package com.sma.admin.config;
public record PreferenceView(String locale,String timeZone,String tenantTimeZone,String theme,boolean tabsEnabled,boolean keepTabsAfterReload,String skin) {
 public PreferenceView(String locale,String timeZone,String tenantTimeZone,String theme,boolean tabsEnabled,boolean keepTabsAfterReload) {
  this(locale,timeZone,tenantTimeZone,theme,tabsEnabled,keepTabsAfterReload,"iris");
 }
}
