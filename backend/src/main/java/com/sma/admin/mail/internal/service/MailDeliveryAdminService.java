package com.sma.admin.mail.internal.service;

import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.common.security.RequiresPermission;
import com.sma.admin.mail.MailDeliveryView;
import com.sma.admin.mail.internal.persistence.MailDeliveryEntity;
import com.sma.admin.mail.internal.persistence.MailDeliveryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class MailDeliveryAdminService {
    private final MailDeliveryMapper deliveries;
    public MailDeliveryAdminService(MailDeliveryMapper deliveries){this.deliveries=deliveries;}
    @RequiresPermission("mail:delivery:view")
    public PageResult<MailDeliveryView> page(PageQuery page,String state,String application) {
        ActorContext.tenantId();var query=new LambdaQueryWrapper<MailDeliveryEntity>().orderByDesc(MailDeliveryEntity::getCreatedAt).orderByDesc(MailDeliveryEntity::getId);
        if(state!=null && !state.isBlank()) {
            if(List.of("DELIVERED","BOUNCED","COMPLAINT").contains(state))query.eq(MailDeliveryEntity::getReceiptState,state);
            else {
                if(!List.of("QUEUED","CLAIMED","SENDING","ACCEPTED","FAILED","UNKNOWN","CANCELLED").contains(state))throw new BizException(CommonErrorCode.BAD_REQUEST);
                query.eq(MailDeliveryEntity::getState,state);
            }
        }
        if(application!=null && !application.isBlank())query.eq(MailDeliveryEntity::getApplication,application);
        return PageResult.of(deliveries.selectPage(page.toPage(Map.of("createdAt","created_at")),query),MailOutboxService::view);
    }
}
