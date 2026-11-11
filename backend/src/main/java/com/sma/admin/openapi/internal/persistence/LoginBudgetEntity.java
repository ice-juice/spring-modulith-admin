package com.sma.admin.openapi.internal.persistence;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;
/** Platform pre-authentication counters, before an application tenant has been established. */
@Getter @Setter @TableName("openapi_login_budget")
public class LoginBudgetEntity extends BaseEntity {
    private String scope;
    private String subjectHash;
    private Integer attemptCount;
    private Instant expiresAt;
}
