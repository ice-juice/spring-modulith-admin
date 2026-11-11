package com.sma.admin.file.internal.persistence;

import java.time.Instant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UploadMapper extends BaseMapper<UploadEntity> {
    @Select("SELECT CURRENT_TIMESTAMP(3)") Instant now();
}
