package com.sma.admin.common.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessOutboxMapper extends BaseMapper<BusinessOutboxEntity> {
    @Insert("""
            INSERT INTO common_business_outbox
                (id, tenant_id, event_type, key_hash, payload_hash, payload, state,
                 created_at, created_by, updated_at, updated_by, deleted)
            VALUES (#{id}, #{tenant}, #{eventType}, #{keyHash}, #{payloadHash}, #{payload}, 'PENDING',
                    UTC_TIMESTAMP(3), #{actor}, UTC_TIMESTAMP(3), #{actor}, 0)
            ON DUPLICATE KEY UPDATE id=id
            """)
    int insertIntent(@Param("id") long id, @Param("tenant") long tenant,
                     @Param("eventType") String eventType, @Param("keyHash") String keyHash,
                     @Param("payloadHash") String payloadHash, @Param("payload") byte[] payload,
                     @Param("actor") long actor);

}
