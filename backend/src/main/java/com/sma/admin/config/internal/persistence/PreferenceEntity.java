package com.sma.admin.config.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("config_user_preference")
public class PreferenceEntity extends TenantEntity {
 private Long userId;private String locale;private String timeZone;private String theme;private Boolean tabsEnabled;private Boolean keepTabsAfterReload;
}
