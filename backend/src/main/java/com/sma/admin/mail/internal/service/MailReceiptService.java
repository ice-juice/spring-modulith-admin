package com.sma.admin.mail.internal.service;

import java.time.temporal.ChronoUnit;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.*;
import com.sma.admin.mail.*;
import com.sma.admin.mail.internal.persistence.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailReceiptService implements MailReceiptApi {
    private final MailDeliveryMapper deliveries;private final MailProfileMapper profiles;
    private final MailReceiptMapper receipts;private final MailSuppressionService suppression;
    private final List<MailReceiptPolicy> policies;private final MailWorkClock clock;
    public MailReceiptService(MailDeliveryMapper deliveries,MailProfileMapper profiles,MailReceiptMapper receipts,
                              MailSuppressionService suppression,List<MailReceiptPolicy> policies,MailWorkClock clock) {
        this.deliveries=deliveries;this.profiles=profiles;this.receipts=receipts;this.suppression=suppression;
        this.policies=List.copyOf(policies);this.clock=clock;
    }
    @Override @Transactional public Result accept(Command c) {
        var actor=ActorContext.require();
        if(actor.kind()!=ActorRef.Kind.APPLICATION)throw new AccessDeniedException("Mail receipt gateway identity required");
        validate(c);
        if(policies.stream().noneMatch(p->p.canAccept(actor,c)))throw new AccessDeniedException("Mail receipt grant required");
        var row=deliveries.selectOne(new LambdaQueryWrapper<MailDeliveryEntity>().eq(MailDeliveryEntity::getMessageId,c.messageId())
                .eq(MailDeliveryEntity::getProfileId,c.profileId()).eq(MailDeliveryEntity::getApplication,c.application()).last("FOR UPDATE"));
        if(row==null)throw new BizException(MailErrorCode.DELIVERY_NOT_FOUND);
        // Lock order matches workers: delivery, then profile; the profile also serializes event IDs across deliveries.
        var profile=profiles.selectOne(new LambdaQueryWrapper<MailProfileEntity>().eq(MailProfileEntity::getId,c.profileId()).last("FOR UPDATE"));
        if(profile==null)throw new BizException(MailErrorCode.PROFILE_NOT_FOUND);
        var previous=receipts.selectOne(new LambdaQueryWrapper<MailReceiptEntity>().eq(MailReceiptEntity::getProfileId,c.profileId())
                .eq(MailReceiptEntity::getProvider,c.provider()).eq(MailReceiptEntity::getEventId,c.eventId()).last("FOR UPDATE"));
        if(previous!=null) {
            if(!previous.getDeliveryId().equals(row.getId()) || !previous.getType().equals(c.type().name())
                    || !previous.getOccurredAt().equals(c.occurredAt().truncatedTo(ChronoUnit.MILLIS)))throw new BizException(MailErrorCode.IDEMPOTENCY_CONFLICT);
            return new Result(row.getId(),row.getReceiptState(),true);
        }
        if(row.getAttempts()<1 || Set.of("CANCELLED","FAILED").contains(row.getState()) || row.getRecipientKey()==null)
            throw new BizException(MailErrorCode.INVALID_STATE);
        var receipt=new MailReceiptEntity();receipt.setProfileId(c.profileId());receipt.setApplication(c.application());
        receipt.setProvider(c.provider());receipt.setEventId(c.eventId());receipt.setDeliveryId(row.getId());receipt.setType(c.type().name());
        receipt.setOccurredAt(c.occurredAt().truncatedTo(ChronoUnit.MILLIS));receipts.insert(receipt);
        if(c.type()!=Type.DELIVERED)suppression.block(row,c.type().name());
        if(rank(c.type().name())>rank(row.getReceiptState()))row.setReceiptState(c.type().name());
        deliveries.updateById(row);
        return new Result(row.getId(),row.getReceiptState(),false);
    }
    private int rank(String state){return state==null?0:switch(state){case "COMPLAINT"->3;case "BOUNCED"->2;case "DELIVERED"->1;default->throw new IllegalStateException("Invalid receipt state");};}
    private void validate(Command c) {
        if(c==null || c.application()==null || !c.application().matches("[a-z][a-z0-9.-]{0,63}") || c.profileId()<=0
                || c.provider()==null || !c.provider().matches("[a-z][a-z0-9.-]{0,63}") || c.eventId()==null
                || !c.eventId().matches("[A-Za-z0-9:._-]{1,128}") || c.messageId()==null
                || !c.messageId().matches("sma-[a-f0-9-]{36}@mail\\.invalid") || c.type()==null || c.occurredAt()==null
                || c.occurredAt().isAfter(clock.now().plusSeconds(300)) || c.occurredAt().isBefore(clock.now().minus(30,ChronoUnit.DAYS)))
            throw new BizException(MailErrorCode.INVALID_CONFIGURATION);
    }
}
