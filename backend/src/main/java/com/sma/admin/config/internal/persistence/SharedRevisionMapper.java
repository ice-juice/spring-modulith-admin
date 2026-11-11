package com.sma.admin.config.internal.persistence;

import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface SharedRevisionMapper extends BaseMapper<SharedRevisionEntity> {
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM config_shared_revision WHERE id=#{id} AND head_id=#{headId} AND deleted=0 FOR SHARE")
    SharedRevisionEntity current(@Param("id") long id, @Param("headId") long headId);
}
