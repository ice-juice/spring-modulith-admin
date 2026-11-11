package com.sma.admin.tenant.internal.persistence;
import org.apache.ibatis.annotations.*;
@Mapper
public interface BrandReferenceMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<BrandReferenceEntity> {
    @Delete("DELETE FROM tenant_brand_reference WHERE portal=#{portal} AND reference_key='draft'")
    int clearDraft(String portal);
    @Select("SELECT id FROM tenant_brand_reference WHERE portal=#{portal} AND file_id=#{fileId} AND deleted=0 LIMIT 1 FOR SHARE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    Long currentReference(String portal,long fileId);
}
