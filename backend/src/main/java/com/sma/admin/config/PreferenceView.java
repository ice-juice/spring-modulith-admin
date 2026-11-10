package com.sma.admin.config;
public record PreferenceView(String locale,String timeZone,String tenantTimeZone,String theme,boolean tabsEnabled,boolean keepTabsAfterReload) {}
