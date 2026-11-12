package com.sma.admin.defense.internal.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface CapacityMapper extends BaseMapper<CapacityEntity> {
    @Select("SELECT * FROM defense_capacity WHERE id=#{id} AND deleted=0 FOR UPDATE")
    CapacityEntity lock(long id);
    @Update("UPDATE defense_capacity SET records=records+#{delta},updated_at=UTC_TIMESTAMP(3) WHERE id=#{id} AND records+#{delta}>=0 AND deleted=0")
    int adjust(long id,long delta);
    @Select("SELECT UTC_TIMESTAMP(3)") java.time.Instant now();
}
