package com.sma.admin.common.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RequestExecutionMapper extends BaseMapper<RequestExecutionEntity> {
    @Select("SELECT UTC_TIMESTAMP(3)") java.time.Instant now();
}
