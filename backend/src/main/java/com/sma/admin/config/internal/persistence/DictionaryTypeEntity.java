package com.sma.admin.config.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("config_dictionary_type")
public class DictionaryTypeEntity extends TenantEntity {
    private String typeKey; private String name; private String nameKey; private Boolean tree; private Boolean builtin; private Boolean enabled; private Integer sort;
}
