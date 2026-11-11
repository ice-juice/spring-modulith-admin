package com.sma.admin.openapi.internal.persistence;
import org.apache.ibatis.annotations.*;
@Mapper
public interface LoginBudgetGuardMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<LoginBudgetGuardEntity> {
    @Insert("""
        INSERT INTO openapi_login_budget_guard(id,key_id,key_cipher,created_at,created_by,updated_at,updated_by,deleted)
        VALUES(1,#{keyId},#{cipher},UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
        ON DUPLICATE KEY UPDATE id=id
        """)
    int ensure(String keyId,String cipher);
}
