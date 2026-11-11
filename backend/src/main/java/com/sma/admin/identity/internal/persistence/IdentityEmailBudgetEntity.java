package com.sma.admin.identity.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("identity_email_budget")
public class IdentityEmailBudgetEntity extends com.sma.admin.common.persistence.TenantEntity {
    private Long minuteSlot;private Long daySlot;private Integer minuteStarts;private Integer dayStarts;
    private Long guessMinute;private Long guessDay;private Integer minuteGuesses;private Integer dayGuesses;
    private Integer outstanding;private Integer retained;
}
