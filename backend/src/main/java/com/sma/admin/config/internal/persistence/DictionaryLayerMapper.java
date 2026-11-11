package com.sma.admin.config.internal.persistence;

import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface DictionaryLayerMapper extends BaseMapper<DictionaryLayerEntity> {
    @Select("SELECT * FROM config_dictionary_layer WHERE deleted=0 AND active_key=#{key} FOR SHARE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    DictionaryLayerEntity current(@Param("key") String key);

    @Update("UPDATE config_dictionary_layer SET enabled=#{enabled},updated_at=UTC_TIMESTAMP(3),updated_by=#{actor} WHERE id=#{id} AND deleted=0")
    int enabled(@Param("id") long id,@Param("enabled") boolean enabled,@Param("actor") long actor);
}
