package com.sma.business.shopdemo.internal.service;

import java.nio.charset.StandardCharsets;
import com.sma.admin.common.request.*;
import com.sma.admin.common.security.*;
import com.sma.admin.common.persistence.DataSourceIdentity;
import com.sma.business.shopdemo.internal.persistence.ProjectionEntity;
import com.sma.business.shopdemo.internal.persistence.ProjectionMapper;
import org.springframework.stereotype.Component;

/** Local projection only; an external receiver needs its own stable-key deduplication protocol. */
@Component
public class OrderCreatedConsumer implements BusinessOutboxConsumer {
    private final BusinessLedger ledger;private final ProjectionMapper projections;
    public OrderCreatedConsumer(BusinessLedger ledger,ProjectionMapper projections){this.ledger=ledger;this.projections=projections;}
    @Override public boolean supports(String type){return "shopdemo.order-created".equals(type);}
    @Override public void deliver(Delivery event){
        if(!supports(event.eventType())||ActorContext.require().kind()!=ActorRef.Kind.SYSTEM_JOB)throw new IllegalStateException("Trusted event worker required");
        ActorContext.requireTenant(event.tenantId());
        long orderId=Long.parseLong(new String(event.payload(),StandardCharsets.UTF_8));
        if(orderId<=0)throw new IllegalArgumentException("Invalid order event");
        try(var session=ledger.open("shopdemo")){
            if(!session.resourceIdentity().equals(new DataSourceIdentity(event.resourceId(),event.resourceRevision())))
                throw new IllegalStateException("Event resource no longer matches the business binding; operator review required");
            session.executeLocal(new RequestCommand("shopdemo.order.project","shopdemo.projection",Long.toString(orderId),event.keyHash(),event.payload(),""),new RequestExecution.Work<Void>(){
                @Override public void authorize(){ActorContext.requireTenant(event.tenantId());}
                @Override public Void execute(){var row=new ProjectionEntity();row.setOrderId(orderId);projections.insert(row);return null;}
            });
        }
    }
}
