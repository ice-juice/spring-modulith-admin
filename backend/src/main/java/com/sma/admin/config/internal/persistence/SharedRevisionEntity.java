package com.sma.admin.config.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Immutable platform shared publication; mutations are not exposed by the owning service. */
@Getter @Setter @TableName("config_shared_revision")
public class SharedRevisionEntity extends BaseEntity {
    private Long headId;
    private Long revision;
    private String contractHash;
    private String contentJson;
    private Integer contentBytes;
}
