package com.sma.admin.config.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface ParameterMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<ParameterEntity> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM config_parameter WHERE deleted=0 AND brand_archived=0 AND active_key=#{key} FOR SHARE")
    @org.apache.ibatis.annotations.Options(useCache=false,flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    ParameterEntity current(@org.apache.ibatis.annotations.Param("key") String key);

    /** Explicit null is a value. MyBatis updateById intentionally skips null fields. */
    @org.apache.ibatis.annotations.Update("""
        UPDATE config_parameter SET param_value=#{value},value_overridden=#{overridden},
          updated_at=UTC_TIMESTAMP(3),updated_by=#{actor} WHERE id=#{id} AND deleted=0
        """)
    int writeValue(@org.apache.ibatis.annotations.Param("id") long id, @org.apache.ibatis.annotations.Param("value") String value,
            @org.apache.ibatis.annotations.Param("overridden") boolean overridden, @org.apache.ibatis.annotations.Param("actor") long actor);
    @org.apache.ibatis.annotations.Select("""
        <script>
        SELECT * FROM config_parameter WHERE deleted=0 AND brand_archived=0 AND param_key IN
        <foreach collection="keys" item="key" open="(" separator="," close=")">#{key}</foreach>
        ORDER BY param_key FOR UPDATE
        </script>
        """)
    @org.apache.ibatis.annotations.Options(useCache=false,flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    java.util.List<ParameterEntity> legacyBrandRows(@org.apache.ibatis.annotations.Param("keys") java.util.Collection<String> keys);
}
