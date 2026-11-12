package com.sma.business.shopdemo.internal;

@org.springframework.stereotype.Component
public class ShopSchemaCatalog implements com.sma.admin.common.persistence.BusinessSchemaCatalog {
    @Override public String storageGroup(){return "shopdemo";}
}
