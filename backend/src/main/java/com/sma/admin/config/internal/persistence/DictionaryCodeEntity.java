package com.sma.admin.config.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Reviewed global namespace metadata. Never stores a private code, tenant identity or dictionary content. */
@Getter @Setter @TableName("config_dictionary_code")
public class DictionaryCodeEntity extends BaseEntity {
    private Long headId;
    private String codeHash;
    private String ownership;
}
