package com.sma.admin.common.internal.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface CacheTaskGateMapper extends BaseMapper<CacheTaskGateEntity> {
    @Insert("""
        INSERT INTO common_cache_task_gate (id,tenant_id,created_at,created_by,updated_at,updated_by,deleted)
        VALUES (#{id},#{tenant},UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
        ON DUPLICATE KEY UPDATE id=id
        """)
    int ensure(long id,long tenant);
}
