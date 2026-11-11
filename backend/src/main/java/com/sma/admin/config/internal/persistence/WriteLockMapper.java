package com.sma.admin.config.internal.persistence;
import org.apache.ibatis.annotations.*;
@Mapper
public interface WriteLockMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<WriteLockEntity> {
    @Insert("""
            INSERT INTO config_write_lock (id,tenant_id,created_at,created_by,updated_at,updated_by,deleted)
            VALUES (#{tenant},#{tenant},UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0) ON DUPLICATE KEY UPDATE id=id
            """)
    int lock(long tenant);
    @Select("SELECT parameter_revision FROM config_write_lock WHERE id=#{tenant} AND tenant_id=#{tenant} AND deleted=0 FOR SHARE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    Long parameterRevision(long tenant);

    @Update("""
        UPDATE config_write_lock SET parameter_revision=parameter_revision+1,updated_at=UTC_TIMESTAMP(3),updated_by=#{actor}
        WHERE id=#{tenant} AND tenant_id=#{tenant} AND deleted=0 AND parameter_revision < 9223372036854775807
        """)
    int advanceParameters(@Param("tenant") long tenant, @Param("actor") long actor);

    @Select("SELECT dictionary_revision FROM config_write_lock WHERE id=#{tenant} AND tenant_id=#{tenant} AND deleted=0 FOR SHARE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    Long dictionaryRevision(long tenant);

    @Update("""
        UPDATE config_write_lock SET dictionary_revision=dictionary_revision+1,updated_at=UTC_TIMESTAMP(3),updated_by=#{actor}
        WHERE id=#{tenant} AND tenant_id=#{tenant} AND deleted=0 AND dictionary_revision < 9223372036854775807
        """)
    int advanceDictionaries(@Param("tenant") long tenant, @Param("actor") long actor);
}
