package com.sma.admin.common.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("common_request_mutex")
public class RequestMutexEntity extends TenantEntity {
    private String resourceHash;
}
