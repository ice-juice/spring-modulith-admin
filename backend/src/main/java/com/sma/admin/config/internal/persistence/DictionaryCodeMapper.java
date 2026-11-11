package com.sma.admin.config.internal.persistence;

import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface DictionaryCodeMapper extends BaseMapper<DictionaryCodeEntity> {
    /** A tombstone cannot free a historical name; callers fail closed on unexpected deletion. */
    @Select("SELECT * FROM config_dictionary_code WHERE head_id=#{head} AND code_hash=#{hash} FOR SHARE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    DictionaryCodeEntity current(@Param("head") long head,@Param("hash") String hash);
}
