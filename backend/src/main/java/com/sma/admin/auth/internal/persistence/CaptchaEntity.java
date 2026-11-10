package com.sma.admin.auth.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("auth_captcha")
public class CaptchaEntity extends TenantEntity {
    private String challengeId; private String answerHash; private Instant expiresAt;
}
