package com.sma.admin.config.internal.persistence;
import org.apache.ibatis.annotations.*;
@Mapper
public interface WriteLockMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<WriteLockEntity> {
    @Insert("""
            INSERT INTO config_write_lock (id,tenant_id,created_at,created_by,updated_at,updated_by,deleted)
            VALUES (#{tenant},#{tenant},UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0) ON DUPLICATE KEY UPDATE id=id
            """)
    int lock(long tenant);
}
