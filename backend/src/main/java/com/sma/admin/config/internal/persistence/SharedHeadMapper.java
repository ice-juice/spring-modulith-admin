package com.sma.admin.config.internal.persistence;

import java.time.Instant;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface SharedHeadMapper extends BaseMapper<SharedHeadEntity> {
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM config_shared_head WHERE id=#{id} AND deleted=0 FOR UPDATE")
    SharedHeadEntity lock(long id);

    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM config_shared_head WHERE kind=#{kind} AND config_key=#{key} AND deleted=0 FOR UPDATE")
    SharedHeadEntity lockKey(@Param("kind") String kind, @Param("key") String key);

    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM config_shared_head WHERE kind=#{kind} AND config_key=#{key} AND deleted=0 FOR SHARE")
    SharedHeadEntity current(@Param("kind") String kind, @Param("key") String key);

    /** A cache hit reads only authority metadata, never the mutable draft or revision body. */
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    @Select("""
        SELECT id,kind,config_key,owner_module,contract_hash,published_revision_id,next_revision
        FROM config_shared_head WHERE kind=#{kind} AND config_key=#{key} AND deleted=0 FOR SHARE
        """)
    SharedHeadEntity pointer(@Param("kind") String kind,@Param("key") String key);

    /** Host-only idempotent registration. A duplicate reserves its existing identity without overwriting user work. */
    @Insert("""
        INSERT INTO config_shared_head(id,kind,config_key,owner_module,definition_json,contract_hash,source_fingerprint,
            draft_json,draft_label_json,draft_version,next_revision,history_bytes,created_at,created_by,updated_at,updated_by,deleted)
        VALUES (#{row.id},#{row.kind},#{row.configKey},#{row.ownerModule},#{row.definitionJson},#{row.contractHash},#{row.sourceFingerprint},
            #{row.draftJson},#{row.draftLabelJson},1,1,0,#{now},0,#{now},0,0)
        ON DUPLICATE KEY UPDATE config_key=config_key
        """)
    int ensure(@Param("row") SharedHeadEntity row, @Param("now") Instant now);
}
