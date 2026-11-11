package com.sma.admin.common.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RequestBudgetMapper extends BaseMapper<RequestBudgetEntity> {
    @Insert("""
        INSERT INTO common_request_budget (id,tenant_id,subject_hash,used_count,expires_at,created_at,created_by,updated_at,updated_by,deleted)
        VALUES (#{id},#{tenant},#{hash},0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
        ON DUPLICATE KEY UPDATE id=id
        """)
    int ensure(long id, long tenant, String hash);

    /** Ephemeral expired counters only. Explicit scope remains present even before tenant SQL interception. */
    @org.apache.ibatis.annotations.Delete("DELETE FROM common_request_budget WHERE tenant_id=#{tenant} AND id=#{id} AND expires_at < #{cutoff}")
    int deleteExpired(long tenant,long id,java.time.Instant cutoff);
}
