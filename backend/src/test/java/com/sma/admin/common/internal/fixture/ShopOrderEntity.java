package com.sma.admin.common.internal.fixture;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sma.admin.common.persistence.TenantEntity;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("s6c_shop_order")
public class ShopOrderEntity extends TenantEntity {
    private String code;
}
