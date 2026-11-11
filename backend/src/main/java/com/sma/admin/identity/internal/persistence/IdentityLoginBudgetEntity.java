package com.sma.admin.identity.internal.persistence;
import com.baomidou.mybatisplus.annotation.*;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter @TableName("identity_login_budget")
public class IdentityLoginBudgetEntity extends TenantEntity {
    private Long minuteSlot;
    private Integer minuteStarts;
    private java.time.LocalDate daySlot;
    private Integer dayStarts;
    private Integer outstanding;
    private Integer retained;
}
