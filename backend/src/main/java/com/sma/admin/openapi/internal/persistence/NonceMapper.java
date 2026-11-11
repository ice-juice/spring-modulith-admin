package com.sma.admin.openapi.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface NonceMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<NonceEntity>{
 @org.apache.ibatis.annotations.Delete("DELETE FROM openapi_nonce WHERE expires_at < UTC_TIMESTAMP(3) LIMIT 200")
 int purge();
}
