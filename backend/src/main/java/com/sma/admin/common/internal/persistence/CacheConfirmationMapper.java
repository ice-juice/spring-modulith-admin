package com.sma.admin.common.internal.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface CacheConfirmationMapper extends BaseMapper<CacheConfirmationEntity> {
    @Select("SELECT UTC_TIMESTAMP(3)") java.time.Instant now();
}
