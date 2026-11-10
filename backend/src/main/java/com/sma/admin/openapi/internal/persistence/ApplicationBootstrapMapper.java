package com.sma.admin.openapi.internal.persistence;
import org.apache.ibatis.annotations.*;
/** The sole application bootstrap lookup: globally unique AppId, followed by secret verification. */
@Mapper
public interface ApplicationBootstrapMapper {
 @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine="true")
 @Select("SELECT * FROM openapi_application WHERE app_id=#{appId} AND deleted=0")
 ApplicationEntity byAppId(String appId);
}
