package com.sma.admin.defense.internal.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface TenantCapacityMapper extends BaseMapper<TenantCapacityEntity> {
    @Delete("DELETE FROM defense_tenant_capacity WHERE id=#{id} AND records=0") int removeEmpty(long id);
    @Select("SELECT * FROM defense_tenant_capacity WHERE tenant_id=#{tenant} AND deleted=0 FOR UPDATE")
    TenantCapacityEntity lock(long tenant);
    @Update("UPDATE defense_tenant_capacity SET records=records+#{delta},updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND records+#{delta}>=0 AND deleted=0")
    int adjust(long id,long delta);
    @Insert("""
        INSERT INTO defense_tenant_capacity(id,tenant_id,records,created_at,created_by,updated_at,updated_by,deleted)
        VALUES(#{id},#{tenant},0,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0) ON DUPLICATE KEY UPDATE id=id
        """) int ensure(long id,long tenant);
}
