package com.sma.admin.config.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("config_parameter")
public class ParameterEntity extends TenantEntity {
 private String paramKey;private String groupKey;private String name;private String nameKey;private String valueType;private String paramValue;
 private Boolean builtin;private Boolean publicReadable;private Boolean secret;private Integer sort;
 private Boolean brandArchived;
 private String definitionContract;
 private Boolean valueOverridden;
 private Boolean presentationOverridden;
}
