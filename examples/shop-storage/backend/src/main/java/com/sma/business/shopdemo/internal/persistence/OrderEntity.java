package com.sma.business.shopdemo.internal.persistence;

@com.baomidou.mybatisplus.annotation.TableName("shopdemo_order")
public class OrderEntity extends com.sma.admin.common.persistence.TenantEntity {
    private String code;
    public String getCode(){return code;}
    public void setCode(String code){this.code=code;}
}
