package com.sma.admin.config.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

/** OVERRIDE rows only carry explicit changes; PRIVATE rows are tenant-owned stable codes. */
@Getter @Setter @TableName("config_dictionary_entry")
public class DictionaryEntryEntity extends TenantEntity {
    private Long layerId;
    private String entryKind;
    private String code;
    private String parentCode;
    private String labelJson;
    private Integer sort;
    private Boolean hidden;
    private Boolean enabled;
    private Long legacyItemId;
}
