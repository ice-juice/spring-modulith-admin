package com.sma.admin.common.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;
/** Host-owned process lease. Not a permission, session, cache value, or distributed-ID lease. */
@Getter @Setter @TableName("common_cache_node_lease")
public class CacheNodeLeaseEntity extends BaseEntity {
    private String clusterHash;
    private String nodeId;
    private String processId;
    private Long epoch;
    private String manifestHash;
    private Instant leaseUntil;
}
