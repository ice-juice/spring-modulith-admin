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
}
