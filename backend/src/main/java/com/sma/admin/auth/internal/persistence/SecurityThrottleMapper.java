package com.sma.admin.auth.internal.persistence;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SecurityThrottleMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<SecurityThrottleEntity> {
    @org.apache.ibatis.annotations.Insert("""
        INSERT INTO auth_throttle (id,tenant_id,scope,subject_hash,attempt_count,expires_at,created_at,created_by,updated_at,updated_by,deleted)
        VALUES (#{id},#{tenant},#{scope},#{subject},0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
        ON DUPLICATE KEY UPDATE id=id
        """)
    int ensure(long id, long tenant, String scope, String subject);

    @org.apache.ibatis.annotations.Select("SELECT UTC_TIMESTAMP(3)")
    java.time.Instant now();
    @org.apache.ibatis.annotations.Delete("DELETE FROM auth_throttle WHERE expires_at < UTC_TIMESTAMP(3) LIMIT 200")
    int removeExpired();
}
