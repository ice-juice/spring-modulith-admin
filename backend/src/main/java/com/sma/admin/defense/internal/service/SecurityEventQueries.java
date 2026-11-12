package com.sma.admin.defense.internal.service;
import java.util.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.*;
import com.sma.admin.defense.*;
import com.sma.admin.defense.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
@Service @Transactional(readOnly=true)
public class SecurityEventQueries implements SecurityEvents {
    private final PlatformEventMapper platform;private final TenantEventMapper tenant;private final DefensePlatformAccess access;private final JsonMapper json;
    public SecurityEventQueries(PlatformEventMapper platform,TenantEventMapper tenant,List<DefensePlatformAccess> providers,JsonMapper json){
        if(providers.size()!=1)throw new IllegalStateException("Exactly one platform event authority is required");
        this.platform=platform;this.tenant=tenant;this.access=providers.getFirst();this.json=json;
    }
    private void tenantAccess(){var actor=CurrentUser.require();
        if(!actor.portal().equals("admin")||!actor.permissions().contains("defense:event:view"))throw new AccessDeniedException("Tenant event access denied");
        ActorContext.requireTenant(CurrentUser.tenantId());
    }
    @Override public List<View> tenant(int limit){tenantAccess();bound(limit);
        return tenant.selectList(new LambdaQueryWrapper<TenantEventEntity>().orderByDesc(TenantEventEntity::getReceivedAt,TenantEventEntity::getId).last("LIMIT "+limit)).stream().map(this::view).toList();}
    @Override public Optional<View> tenant(UUID id){tenantAccess();Objects.requireNonNull(id);
        return Optional.ofNullable(tenant.selectOne(new LambdaQueryWrapper<TenantEventEntity>().eq(TenantEventEntity::getEventId,id.toString()))).map(this::view);}
    @Override public List<View> platform(int limit){access.requireViewer();bound(limit);
        return platform.selectList(new LambdaQueryWrapper<PlatformEventEntity>().orderByDesc(PlatformEventEntity::getReceivedAt,PlatformEventEntity::getId).last("LIMIT "+limit)).stream().map(this::view).toList();}
    @Override public Optional<View> platform(UUID id){access.requireViewer();Objects.requireNonNull(id);
        return Optional.ofNullable(platform.selectOne(new LambdaQueryWrapper<PlatformEventEntity>().eq(PlatformEventEntity::getEventId,id.toString()))).map(this::view);}
    private void bound(int limit){if(limit<1||limit>100)throw new IllegalArgumentException("Event page bound exceeded");}
    private View view(PlatformEventEntity row){return new View(UUID.fromString(row.getEventId()),UUID.fromString(row.getCorrelationId()),row.getOccurredAt(),row.getReceivedAt(),
        row.getExpiresAt(),row.getFact(),row.getCategory(),row.getSeverity(),row.getOutcome(),row.getAssessment(),row.getRuleVersion(),json.readValue(row.getPayload(),SecuritySignal.class),row.getHeld());}
    private View view(TenantEventEntity row){return new View(UUID.fromString(row.getEventId()),UUID.fromString(row.getCorrelationId()),row.getOccurredAt(),row.getReceivedAt(),
        row.getExpiresAt(),row.getFact(),row.getCategory(),row.getSeverity(),row.getOutcome(),row.getAssessment(),row.getRuleVersion(),json.readValue(row.getPayload(),SecuritySignal.class),row.getHeld());}
}
