package com.sma.business.shopdemo.internal.persistence;

@com.baomidou.mybatisplus.annotation.TableName("shopdemo_projection")
public class ProjectionEntity extends com.sma.admin.common.persistence.TenantEntity {
    private Long orderId;
    public Long getOrderId(){return orderId;}
    public void setOrderId(Long orderId){this.orderId=orderId;}
}
