package com.sma.admin.mail.internal.service;
import java.util.*;
import com.sma.admin.mail.*;
import com.sma.admin.mail.internal.persistence.MailDeliveryEntity;
import org.springframework.stereotype.Component;

@Component
public class MailDeliveryGuards {
    private final Map<String,MailDeliveryGate> gates;
    public MailDeliveryGuards(List<MailDeliveryGate> implementations){
        Map<String,MailDeliveryGate> result=new HashMap<>();for(var gate:implementations){
            if(gate.owner()==null || !gate.owner().matches("[a-z][a-z0-9.-]{0,63}") || result.putIfAbsent(gate.owner(),gate)!=null)throw new IllegalStateException("Invalid or duplicate mail validity gate");
        }gates=Map.copyOf(result);
    }
    boolean valid(MailSendApi.Guard guard){return guard==null || guard.owner()!=null && gates.containsKey(guard.owner())
            && guard.reference()!=null && guard.reference().matches("[A-Za-z0-9:._-]{1,128}");}
    boolean mayDeliver(MailDeliveryEntity row){
        if(row.getGuardOwner()==null)return row.getGuardReference()==null;
        var gate=gates.get(row.getGuardOwner());return gate!=null && row.getGuardReference()!=null
                && gate.mayDeliver(new MailDeliveryGate.Delivery(row.getTenantId(),row.getId(),row.getApplication(),row.getScene(),row.getGuardReference(),row.getExpiresAt()));
    }
}
