package com.sma.admin.auth.internal.persistence;
import java.time.Instant;
import org.apache.ibatis.annotations.*;
@Mapper
public interface CaptchaMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<CaptchaEntity> {
    @Delete("DELETE FROM auth_captcha WHERE expires_at < #{now}")
    int removeExpired(Instant now);
}
