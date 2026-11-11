package com.sma.admin.tenant.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.security.*;
import com.sma.admin.config.LegacyBrandSource;
import com.sma.admin.tenant.internal.persistence.*;
import tools.jackson.databind.json.JsonMapper;

/** One platform transaction per trusted tenant: all portals, immutable first publication, receipt and source archive. */
@Service
public class LegacyBrandMigrationService {
    private final LegacyBrandSource source;
    private final LegacyBrandConverter converter;
    private final TenantRecordMapper tenants;
    private final DomainMapper domains;
    private final BrandHeadMapper heads;
    private final BrandRevisionMapper revisions;
    private final BrandImportMapper imports;
    private final JsonMapper json;
    public LegacyBrandMigrationService(LegacyBrandSource source,LegacyBrandConverter converter,TenantRecordMapper tenants,DomainMapper domains,
            BrandHeadMapper heads,BrandRevisionMapper revisions,BrandImportMapper imports,JsonMapper json) {
        this.source=source;this.converter=converter;this.tenants=tenants;this.domains=domains;this.heads=heads;this.revisions=revisions;this.imports=imports;this.json=json;
    }
    public record Result(boolean imported,boolean replay,String sourceRevision) {}
    @Transactional public Result migrateCurrentTenant() {
        requireHost();long tenantId=CurrentUser.tenantId();
        var tenant=tenants.selectOne(new LambdaQueryWrapper<TenantRecordEntity>().eq(TenantRecordEntity::getId,tenantId).last("FOR UPDATE"));
        if(tenant==null) throw new IllegalStateException("Legacy brand tenant is not registered");
        var snapshot=source.snapshot();
        var previous=imports.selectOne(new LambdaQueryWrapper<BrandImportEntity>().last("FOR UPDATE"));
        if(previous!=null) {
            if(!snapshot.values().isEmpty()) throw new IllegalStateException("New legacy brand values appeared after migration");
            return new Result(false,true,previous.getSourceRevision());
        }
        if(snapshot.values().isEmpty()) return new Result(false,false,snapshot.sourceRevision());
        var converted=converter.convert(snapshot,tenant.getName());
        SortedSet<String> portals=new TreeSet<>();
        domains.selectList(new LambdaQueryWrapper<DomainEntity>().eq(DomainEntity::getOwnerTenantId,tenantId)).forEach(domain->portals.add(domain.getPortal()));
        if(portals.isEmpty()) portals.add("admin");
        if(portals.size()>100) throw new IllegalStateException("Legacy brand migration portal budget exceeded");
        String content=json.writeValueAsString(converted.content());
        String background=converted.legacyBackgroundUrl().isEmpty()?null:converted.legacyBackgroundUrl();
        for(String portal:portals) {
            heads.ensure(IdWorker.getId(),tenantId,portal,content,0);
            var head=heads.selectOne(new LambdaQueryWrapper<BrandHeadEntity>().eq(BrandHeadEntity::getPortal,portal).last("FOR UPDATE"));
            if(head.getDraftVersion()!=0 || head.getPublishedRevisionId()!=null || head.getNextRevision()!=1)
                throw new IllegalStateException("Existing brand draft/publication in portal "+portal+" requires explicit migration reconciliation");
            var revision=new BrandRevisionEntity();revision.setPortal(portal);revision.setRevision(1L);revision.setContentJson(content);
            revision.setContentHash(digest(json.writeValueAsString(Arrays.asList(converted.content(),background))));
            revision.setOperationId("legacy-import-v1");revision.setOperationHash(snapshot.sourceRevision());revision.setPublisherId(0L);
            revision.setDefaultsRevision("legacy-parameters-v1");revision.setLegacyBackgroundUrl(background);revisions.insert(revision);
            head.setDraftJson(content);head.setDraftVersion(1L);head.setPublishedRevisionId(revision.getId());head.setNextRevision(2L);
            head.setLegacyBackgroundUrl(background);heads.updateById(head);
        }
        var receipt=new BrandImportEntity();receipt.setSourceRevision(snapshot.sourceRevision());receipt.setPortalsJson(json.writeValueAsString(portals));
        receipt.setExcludedKeysJson(json.writeValueAsString(converted.excludedKeys().stream().map(Enum::name).sorted().toList()));imports.insert(receipt);
        source.archive(snapshot.sourceRevision());
        return new Result(true,false,snapshot.sourceRevision());
    }
    private void requireHost() {
        if(TenantEntryScope.isHttpRequest() || TenantEntryScope.optional().isPresent()
                || org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()!=null
                || org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()!=null)
            throw new AccessDeniedException("Legacy brand migration is a host upgrade operation");
    }
    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
