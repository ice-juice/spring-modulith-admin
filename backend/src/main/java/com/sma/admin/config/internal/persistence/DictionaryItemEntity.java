package com.sma.admin.config.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("config_dictionary_item")
public class DictionaryItemEntity extends TenantEntity {
    private Long typeId; private Long parentId; private String itemValue; private String name; private String nameKey; private Boolean builtin; private Boolean enabled; private Integer sort;
}
