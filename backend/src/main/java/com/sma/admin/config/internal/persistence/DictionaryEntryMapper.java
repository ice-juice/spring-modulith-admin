package com.sma.admin.config.internal.persistence;

import java.util.List;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface DictionaryEntryMapper extends BaseMapper<DictionaryEntryEntity> {
    /** At most 1024 active rows are valid (512 overrides + 512 private codes); 1025 lets the caller fail closed. */
    @Select("SELECT * FROM config_dictionary_entry WHERE deleted=0 AND layer_id=#{layer} ORDER BY id LIMIT 1025 FOR SHARE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    List<DictionaryEntryEntity> current(@Param("layer") long layer);

    /** Explicit nulls mean inheritance; MyBatis-Plus updateById would silently keep the old value. */
    @Update("""
        UPDATE config_dictionary_entry SET parent_code=#{row.parentCode},label_json=#{row.labelJson},sort=#{row.sort},hidden=#{row.hidden},
          enabled=#{row.enabled},updated_at=UTC_TIMESTAMP(3),updated_by=#{actor} WHERE id=#{row.id} AND deleted=0
        """)
    int replace(@Param("row") DictionaryEntryEntity row,@Param("actor") long actor);
}
