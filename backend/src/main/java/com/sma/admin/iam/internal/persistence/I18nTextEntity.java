package com.sma.admin.iam.internal.persistence;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("iam_i18n_text")
public class I18nTextEntity extends TenantEntity {
    private String entityType;
    private Long entityId;
    private String field;
    private String locale;
    private String text;
}
