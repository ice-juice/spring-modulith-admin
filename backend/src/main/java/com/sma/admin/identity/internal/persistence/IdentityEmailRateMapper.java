package com.sma.admin.identity.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface IdentityEmailRateMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<IdentityEmailRateEntity> {
    @org.apache.ibatis.annotations.Delete("DELETE FROM identity_email_rate WHERE id=#{id}") int purge(long id);
}
