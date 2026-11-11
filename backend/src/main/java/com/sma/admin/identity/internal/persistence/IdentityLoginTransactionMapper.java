package com.sma.admin.identity.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface IdentityLoginTransactionMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<IdentityLoginTransactionEntity> {
    @org.apache.ibatis.annotations.Delete("DELETE FROM identity_login_transaction WHERE id=#{id}")
    int purge(long id);
}
