package com.sma.admin.identity.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface IdentityEmailChallengeMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<IdentityEmailChallengeEntity> {
    @org.apache.ibatis.annotations.Delete("DELETE FROM identity_email_challenge WHERE id=#{id}") int purge(long id);
}
