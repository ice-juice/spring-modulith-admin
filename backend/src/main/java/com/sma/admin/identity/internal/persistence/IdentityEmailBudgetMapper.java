package com.sma.admin.identity.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface IdentityEmailBudgetMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<IdentityEmailBudgetEntity> {
    @org.apache.ibatis.annotations.Insert("INSERT INTO identity_email_budget(id,tenant_id,created_at,created_by,updated_at,updated_by,deleted) VALUES(#{id},#{tenant},#{now},0,#{now},0,0) ON DUPLICATE KEY UPDATE id=identity_email_budget.id")
    int ensure(long id,long tenant,java.time.Instant now);
}
