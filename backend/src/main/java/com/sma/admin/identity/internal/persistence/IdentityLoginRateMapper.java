package com.sma.admin.identity.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface IdentityLoginRateMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<IdentityLoginRateEntity> {
    @org.apache.ibatis.annotations.Delete("DELETE FROM identity_login_rate WHERE id=#{id}")
    int purge(long id);
}
