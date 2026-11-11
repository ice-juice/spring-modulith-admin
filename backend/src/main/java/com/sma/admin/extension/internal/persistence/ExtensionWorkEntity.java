package com.sma.admin.extension.internal.persistence;

import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("extension_work")
public class ExtensionWorkEntity extends TenantEntity {
    private String deploymentId;
    private String pluginId;
    private String entryId;
    private String permission;
    private String workKeyHash;
    private String ownerSubject;
    private String portal;
    private String inputSha256;
    private String selectionJson;
    private String membersJson;
    private String state;
    private String claimId;
    private String resultBase64;
    private String failure;
    private Instant startedAt;
    private Instant completedAt;
    private String reconciliationEvidence;
}
