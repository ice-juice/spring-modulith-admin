package com.sma.admin.common.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RequestMutexMapper extends BaseMapper<RequestMutexEntity> {
    @Insert("""
        INSERT INTO common_request_mutex (id,tenant_id,resource_hash,created_at,created_by,updated_at,updated_by,deleted)
        VALUES (#{id},#{tenant},#{hash},UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
        ON DUPLICATE KEY UPDATE id=id
        """)
    int ensure(long id, long tenant, String hash);
}
