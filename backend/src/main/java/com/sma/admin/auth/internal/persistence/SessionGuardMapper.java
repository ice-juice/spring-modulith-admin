package com.sma.admin.auth.internal.persistence;
import org.apache.ibatis.annotations.*;
@Mapper
public interface SessionGuardMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<SessionGuardEntity> {
    @Insert("""
            INSERT INTO auth_session_guard (id,tenant_id,created_at,created_by,updated_at,updated_by,deleted)
            VALUES (#{userId},#{tenantId},UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
            ON DUPLICATE KEY UPDATE id=id
            """)
    int lock(long userId,long tenantId);
}
