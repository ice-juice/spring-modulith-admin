package com.sma.admin.config.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Platform definitions and shared values only. Never holds a tenant override or a connection credential. */
@Getter @Setter @TableName("config_shared_head")
public class SharedHeadEntity extends BaseEntity {
    private String kind;
    private String configKey;
    private String ownerModule;
    private String definitionJson;
    private String contractHash;
    private String sourceFingerprint;
    private String draftJson;
    private String draftLabelJson;
    private Long draftVersion = 1L;
    private Long publishedRevisionId;
    private Long nextRevision = 1L;
    private Long historyBytes = 0L;
    private Long dictionaryCodeCount = 0L;
}
