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
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String evidenceNonce;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String evidenceProcessId;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private Long evidenceEpoch;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String evidenceDigest;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String evidenceState;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private Instant evidenceUntil;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String storageReportJson;
}
