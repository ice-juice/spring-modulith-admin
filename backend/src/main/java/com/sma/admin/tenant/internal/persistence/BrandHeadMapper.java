package com.sma.admin.tenant.internal.persistence;

import org.apache.ibatis.annotations.*;

@Mapper
public interface BrandHeadMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<BrandHeadEntity> {
    @Insert("""
        INSERT INTO tenant_brand_head (id,tenant_id,portal,draft_json,draft_version,published_revision_id,next_revision,
            created_at,created_by,updated_at,updated_by,deleted)
        VALUES (#{id},#{tenant},#{portal},#{draft},0,NULL,1,UTC_TIMESTAMP(3),#{actor},UTC_TIMESTAMP(3),#{actor},0)
        ON DUPLICATE KEY UPDATE id=id
        """)
    int ensure(long id,long tenant,String portal,String draft,long actor);
}
