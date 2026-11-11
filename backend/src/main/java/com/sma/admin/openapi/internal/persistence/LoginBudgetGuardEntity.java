package com.sma.admin.openapi.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;
/** One platform capacity lock and encrypted immutable budget fingerprint key. Never a tenant directory. */
@Getter @Setter @TableName("openapi_login_budget_guard")
public class LoginBudgetGuardEntity extends BaseEntity {
    private String keyId;
    private String keyCipher;
}
