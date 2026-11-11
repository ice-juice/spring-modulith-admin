package com.sma.admin.tenant.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface BrandAssetMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<BrandAssetEntity> {
    @org.apache.ibatis.annotations.Select("SELECT COALESCE(SUM(OCTET_LENGTH(image_bytes)),0) FROM tenant_brand_asset WHERE portal=#{portal} AND deleted=0")
    long storedBytes(String portal);
}
