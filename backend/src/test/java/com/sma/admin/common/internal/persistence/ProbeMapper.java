package com.sma.admin.common.internal.persistence;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface ProbeMapper extends BaseMapper<ProbeEntity> {
    @org.apache.ibatis.annotations.Update("UPDATE common_test_record SET name='unsafe'")
    int updateAllUnsafely();
}
