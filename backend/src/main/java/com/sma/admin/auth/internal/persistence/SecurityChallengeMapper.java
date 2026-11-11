package com.sma.admin.auth.internal.persistence;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SecurityChallengeMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<SecurityChallengeEntity> {
    @org.apache.ibatis.annotations.Delete("DELETE FROM auth_challenge WHERE expires_at < UTC_TIMESTAMP(3) LIMIT 200")
    int removeExpired();
}
