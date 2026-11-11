package com.sma.admin.extension.internal.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** Platform lifecycle counters contain no business input or result; mutations occur only with tenant-scoped work transactions. */
@Getter @Setter @TableName("extension_runtime_reference")
public class RuntimeReferenceEntity {
    @TableId(type=IdType.INPUT) private String id;
    private String deploymentId;
    private String bundleId;
    private String artifactHash;
    private Long referenceCount;
}
