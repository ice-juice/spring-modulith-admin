package com.sma.admin.common.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;
/** Host-owned global deployment metadata; no tenant or user data and no HTTP mutation. */
@Getter @Setter @TableName("common_cache_node_group")
public class CacheNodeGroupEntity extends BaseEntity {
    private String clusterHash;
    private String manifestHash;
    private Long manifestVersion;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String evidenceManifestHash;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String evidenceNonce;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String evidenceDigest;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String storageManifestHash;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String storageAnchorJson;
}
