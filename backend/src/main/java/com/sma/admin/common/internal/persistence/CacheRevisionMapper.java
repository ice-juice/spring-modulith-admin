package com.sma.admin.common.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CacheRevisionMapper extends BaseMapper<CacheRevisionEntity> {
    @Insert("""
            INSERT INTO common_cache_revision
                (id, tenant_id, region, revision, created_at, created_by, updated_at, updated_by, deleted)
            VALUES (#{id}, #{tenant}, #{region}, 1, UTC_TIMESTAMP(3), #{actor}, UTC_TIMESTAMP(3), #{actor}, 0)
            ON DUPLICATE KEY UPDATE revision=revision+1, updated_at=UTC_TIMESTAMP(3), updated_by=#{actor}
            """)
    int advance(@Param("id") long id, @Param("tenant") long tenant, @Param("region") String region, @Param("actor") long actor);

    @Insert("""
            INSERT INTO common_cache_revision
                (id, tenant_id, region, revision, created_at, created_by, updated_at, updated_by, deleted)
            VALUES (#{id}, #{tenant}, #{region}, 0, UTC_TIMESTAMP(3), #{actor}, UTC_TIMESTAMP(3), #{actor}, 0)
            ON DUPLICATE KEY UPDATE id=id
            """)
    int ensureControlRow(@Param("id") long id, @Param("tenant") long tenant, @Param("region") String region, @Param("actor") long actor);
}
