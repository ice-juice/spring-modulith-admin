package com.sma.admin.common.internal.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface CacheNodeGroupMapper extends BaseMapper<CacheNodeGroupEntity> {
    @Insert("""
        INSERT INTO common_cache_node_group (id,cluster_hash,manifest_hash,manifest_version,created_at,created_by,updated_at,updated_by,deleted)
        VALUES (#{id},#{cluster},#{manifest},#{version},UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
        ON DUPLICATE KEY UPDATE id=id
        """) int ensure(long id,String cluster,String manifest,long version);
    @Select("SELECT UTC_TIMESTAMP(3)") java.time.Instant now();
    @Update("""
        UPDATE common_cache_node_group SET evidence_manifest_hash=#{manifest},evidence_nonce=#{nonce},evidence_digest=NULL,
        updated_at=UTC_TIMESTAMP(3),updated_by=0 WHERE id=#{id} AND manifest_hash=#{manifest} AND deleted=0
        """) int initializeEvidence(long id,String manifest,String nonce);
    @Update("""
        UPDATE common_cache_node_group SET evidence_digest=#{digest},updated_at=UTC_TIMESTAMP(3),updated_by=0
        WHERE id=#{id} AND evidence_nonce=#{nonce} AND evidence_digest IS NULL AND deleted=0
        """) int establishEvidence(long id,String nonce,String digest);
    @Update("""
        UPDATE common_cache_node_group SET storage_manifest_hash=#{manifest},storage_anchor_json=#{anchor},
        updated_at=UTC_TIMESTAMP(3),updated_by=0 WHERE id=#{id} AND manifest_hash=#{manifest} AND deleted=0
        """) int initializeStorage(long id,String manifest,String anchor);
}
