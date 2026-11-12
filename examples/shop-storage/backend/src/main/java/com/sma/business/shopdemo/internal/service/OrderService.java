package com.sma.business.shopdemo.internal.service;

import java.nio.charset.StandardCharsets;
import com.sma.admin.common.cache.BusinessCache;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.business.shopdemo.internal.persistence.OrderEntity;
import com.sma.business.shopdemo.internal.persistence.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

@Service
public class OrderService {
    private final BusinessLedger ledger;private final BusinessCache cache;private final OrderMapper orders;
    public OrderService(BusinessLedger ledger,BusinessCache cache,OrderMapper orders){this.ledger=ledger;this.cache=cache;this.orders=orders;}
    public RequestExecution.Outcome<Long> create(String code,String operationKey){
        if(code==null||!code.matches("[a-zA-Z0-9-]{1,32}")||operationKey==null||!operationKey.matches("[a-zA-Z0-9_-]{16,64}"))
            throw new IllegalArgumentException("Invalid order code or operation key");
        try(var session=ledger.open("shopdemo")){
            return session.executeLocal(new RequestCommand("shopdemo.order.create","shopdemo.order",code,operationKey,
                    code.getBytes(StandardCharsets.UTF_8),"shopdemo:order:create"),new RequestExecution.Work<Long>(){
                @Override public void authorize(){require("shopdemo:order:create");}
                @Override public Long execute(){
                    var order=new OrderEntity();order.setCode(code);orders.insert(order);
                    session.invalidate("shopdemo.orders");
                    session.enqueue("shopdemo.order-created",operationKey,Long.toString(order.getId()).getBytes(StandardCharsets.UTF_8));
                    return order.getId();
                }
            });
        }
    }
    public long count(){require("shopdemo:order:view");return cache.get("shopdemo.orders","count",Long.class,()->orders.selectCount(null));}
    public RequestReceipt receipt(String originalKey){require("shopdemo:order:create");try(var session=ledger.open("shopdemo")){return session.receiptByKey(originalKey);}}
    private static void require(String permission){if(!CurrentUser.require().permissions().contains(permission))throw new AccessDeniedException("Order permission required");}
}
