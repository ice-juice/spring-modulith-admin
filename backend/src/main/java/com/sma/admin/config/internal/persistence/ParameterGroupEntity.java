package com.sma.admin.config.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("config_parameter_group")
public class ParameterGroupEntity extends TenantEntity { private String groupKey;private String name;private String nameKey;private Boolean builtin;private Integer sort; }
