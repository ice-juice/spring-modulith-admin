package com.sma.admin.openapi.internal.persistence;
import java.time.Instant;
import org.apache.ibatis.annotations.*;
@Mapper
public interface LoginBudgetMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<LoginBudgetEntity> {
    @Select("SELECT UTC_TIMESTAMP(3)") Instant now();
    @Delete("DELETE FROM openapi_login_budget WHERE expires_at<=UTC_TIMESTAMP(3) LIMIT 200") int removeExpired();
    @Insert("""
        INSERT INTO openapi_login_budget(id,scope,subject_hash,attempt_count,expires_at,created_at,created_by,updated_at,updated_by,deleted)
        VALUES(#{id},#{scope},#{hash},1,#{expires},UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
        """)
    int create(long id,String scope,String hash,Instant expires);
    @Update("UPDATE openapi_login_budget SET attempt_count=#{count},expires_at=#{expires},updated_at=UTC_TIMESTAMP(3),updated_by=0 WHERE id=#{id} AND deleted=0")
    int reserve(long id,int count,Instant expires);
}
