package com.sma.admin.defense.internal.persistence;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
@Mapper
public interface PlatformEventMapper extends BaseMapper<PlatformEventEntity> {
    @Delete("DELETE FROM defense_platform_event WHERE id=#{id} AND held=0 AND expires_at<=#{now}") int purge(long id,java.time.Instant now);
    @Update("UPDATE defense_platform_event SET held=#{held} WHERE id=#{id} AND deleted=0") int hold(long id,boolean held);
}
