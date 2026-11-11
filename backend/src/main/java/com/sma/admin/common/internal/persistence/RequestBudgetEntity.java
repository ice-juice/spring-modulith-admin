package com.sma.admin.common.internal.persistence;

import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("common_request_budget")
public class RequestBudgetEntity extends TenantEntity {
    private String subjectHash;
    private Integer usedCount;
    private Instant expiresAt;
}
