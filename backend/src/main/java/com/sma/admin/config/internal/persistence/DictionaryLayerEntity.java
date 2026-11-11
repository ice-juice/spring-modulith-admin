package com.sma.admin.config.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("config_dictionary_layer")
public class DictionaryLayerEntity extends TenantEntity {
    private String dictionaryKey;
    private String definitionContract;
    private Long legacyTypeId;
    private Boolean enabled;
    private String labelJson;
}
