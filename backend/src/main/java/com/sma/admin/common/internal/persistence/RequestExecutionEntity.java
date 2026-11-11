package com.sma.admin.common.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("common_request_execution")
public class RequestExecutionEntity extends TenantEntity {
    private String publicId;
    private String actorHash;
    private String operation;
    private String resourceHash;
    private String keyHash;
    private String inputHash;
    private String fingerprintKeyId;
    private String permissionKey;
    private String state;
}
