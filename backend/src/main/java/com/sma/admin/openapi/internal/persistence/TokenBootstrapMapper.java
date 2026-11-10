package com.sma.admin.openapi.internal.persistence;
import org.apache.ibatis.annotations.*;
/** Locates a bearer token by its globally unique SHA-256 hash before a tenant is known. */
@Mapper
public interface TokenBootstrapMapper {
 @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine="true")
 @Select("SELECT * FROM openapi_token WHERE token_hash=#{hash} AND deleted=0")
 TokenEntity byHash(String hash);
}
