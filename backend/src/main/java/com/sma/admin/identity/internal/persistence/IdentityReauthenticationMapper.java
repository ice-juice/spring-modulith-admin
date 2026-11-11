package com.sma.admin.identity.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface IdentityReauthenticationMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<IdentityReauthenticationEntity> {
    @org.apache.ibatis.annotations.Delete("DELETE FROM identity_reauthentication WHERE id=#{id}") int purge(long id);
}
