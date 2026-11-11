package com.sma.admin.tenant.internal.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.databind.json.JsonMapper;
import com.sma.admin.common.*;
import com.sma.admin.common.security.*;
import com.sma.admin.tenant.*;
import com.sma.admin.tenant.internal.persistence.*;

@Service @Transactional(readOnly=true) @EnableConfigurationProperties(BrandDefaultsProperties.class)
public class BrandPublicationService {
    private final BrandHeadMapper heads;
    private final BrandRevisionMapper revisions;
    private final BrandAssetMapper images;
    private final BrandAssets assets;
    private final BrandValidation validation;
    private final BrandResolver resolver;
    private final BrandDefaultsProperties defaults;
    private final JsonMapper json;
    public BrandPublicationService(BrandHeadMapper heads,BrandRevisionMapper revisions,BrandAssetMapper images,BrandAssets assets,
            BrandValidation validation,BrandResolver resolver,BrandDefaultsProperties defaults,JsonMapper json) {
        this.heads=heads;this.revisions=revisions;this.images=images;this.assets=assets;this.validation=validation;this.resolver=resolver;this.defaults=defaults;this.json=json;
    }
    public record Receipt(Long revisionId,long revision,Long sourceRevisionId,String operationId) {}
    public record Revision(Long id,long revision,Long sourceRevisionId,String defaultsRevision,BrandContent content,java.time.Instant createdAt,String legacyBackgroundUrl) {
        public Revision(Long id,long revision,Long sourceRevisionId,String defaultsRevision,BrandContent content,java.time.Instant createdAt) {
            this(id,revision,sourceRevisionId,defaultsRevision,content,createdAt,null);
        }
    }

    @Transactional public Receipt publish(String operationId,long expectedDraftVersion,Long expectedPublishedRevisionId) {
        require("tenant:brand:publish");operation(operationId);
        if(expectedDraftVersion<1 || expectedPublishedRevisionId!=null&&expectedPublishedRevisionId<=0) throw invalid();
        var head=lock();String intent=hash("PUBLISH:"+expectedDraftVersion+":"+expectedPublishedRevisionId);
        var replay=replay(operationId,intent);if(replay!=null) return replay;
        if(head.getDraftVersion()!=expectedDraftVersion || !Objects.equals(head.getPublishedRevisionId(),expectedPublishedRevisionId)) throw stale();
        var content=validation.validate(json.readValue(head.getDraftJson(),BrandContent.class));
        content=validation.validate(resolver.resolve(content,defaults.content()));
        var prepared=assets.prepare(content);
        return append(head,content,null,operationId,intent,defaults.revision(),head.getLegacyBackgroundUrl(),prepared);
    }
    @Transactional public Receipt rollback(String operationId,long sourceRevisionId,Long expectedPublishedRevisionId) {
        require("tenant:brand:rollback");operation(operationId);
        if(sourceRevisionId<=0 || expectedPublishedRevisionId==null || expectedPublishedRevisionId<=0) throw invalid();
        var head=lock();String intent=hash("ROLLBACK:"+sourceRevisionId+":"+expectedPublishedRevisionId);
        var replay=replay(operationId,intent);if(replay!=null) return replay;
        if(!Objects.equals(head.getPublishedRevisionId(),expectedPublishedRevisionId)) throw stale();
        var source=revision(sourceRevisionId);var content=validation.validate(json.readValue(source.getContentJson(),BrandContent.class));
        return append(head,content,source.getId(),operationId,intent,source.getDefaultsRevision(),source.getLegacyBackgroundUrl(),assets.existing(source.getId(),content));
    }
    public PageResult<Revision> history(PageQuery page) {
        require("tenant:brand:view");
        var rows=revisions.selectPage(page.<BrandRevisionEntity>toPage(Map.of()),scope().orderByDesc(BrandRevisionEntity::getRevision));
        return new PageResult<>(rows.getRecords().stream().map(this::view).toList(),rows.getTotal(),rows.getCurrent(),rows.getSize());
    }
    public Revision get(long id) { require("tenant:brand:view");return view(revision(id)); }
    public record OperationResult(String status,Receipt receipt) {}
    /** NOT_OBSERVED does not prove failure: the original request may still be in flight. */
    public OperationResult operationResult(String operationId) {
        require("tenant:brand:view");operation(operationId);
        var row=revisions.selectOne(scope().eq(BrandRevisionEntity::getOperationId,operationId)
                .eq(BrandRevisionEntity::getPublisherId,CurrentUser.userId()));
        if(row==null) return new OperationResult("NOT_OBSERVED",null);
        require(row.getSourceRevisionId()==null?"tenant:brand:publish":"tenant:brand:rollback");
        return new OperationResult("COMMITTED",receipt(row));
    }
    /** Used by the typed entry facade. A trusted resolved entry is mandatory; drafts are never consulted. */
    public Optional<Revision> published() {
        var entry=TenantEntryScope.require();if(entry.tenantId()!=CurrentUser.tenantId() || !entry.portal().equals(CurrentUser.portal())) throw new AccessDeniedException("Brand entry mismatch");
        var head=heads.selectOne(new LambdaQueryWrapper<BrandHeadEntity>().eq(BrandHeadEntity::getPortal,entry.portal()));
        return head==null||head.getPublishedRevisionId()==null?Optional.empty():Optional.of(view(revision(head.getPublishedRevisionId())));
    }
    public com.sma.admin.file.ImagePublication.Image publicImage(long revisionId,BrandContent.AssetSlot slot) {
        return findPublicImage(revisionId,slot).orElseThrow(()->new BizException(CommonErrorCode.DATA_NOT_FOUND));
    }
    public Optional<com.sma.admin.file.ImagePublication.Image> findPublicImage(long revisionId,BrandContent.AssetSlot slot) {
        var entry=TenantEntryScope.require();
        if(entry.tenantId()!=CurrentUser.tenantId() || !entry.portal().equals(CurrentUser.portal())) throw new AccessDeniedException("Brand entry mismatch");
        // Absence is a normal public lookup result, not an exception that marks a shared read transaction rollback-only.
        if(revisions.selectOne(scope().eq(BrandRevisionEntity::getId,revisionId))==null) return Optional.empty();
        var row=images.selectOne(new LambdaQueryWrapper<BrandAssetEntity>().eq(BrandAssetEntity::getPortal,entry.portal())
                .eq(BrandAssetEntity::getRevisionId,revisionId).eq(BrandAssetEntity::getSlot,slot.name()));
        if(row==null) return Optional.empty();
        return Optional.of(new com.sma.admin.file.ImagePublication.Image(row.getImageBytes(),"image/png",row.getChecksum(),row.getWidth(),row.getHeight()));
    }
    private Receipt append(BrandHeadEntity head,BrandContent content,Long source,String operation,String intent,String defaultsRevision,String legacyBackgroundUrl,
            Map<Long,com.sma.admin.file.ImagePublication.Image> prepared) {
        long additional=content.assets().values().stream().mapToLong(id->prepared.get(id).bytes().length).sum();
        if(head.getNextRevision()>100 || images.storedBytes(CurrentUser.portal())+additional>64L*1024*1024) throw new BizException(TenantErrorCode.BRAND_CAPACITY);
        String payload=json.writeValueAsString(content);
        var row=new BrandRevisionEntity();row.setPortal(CurrentUser.portal());row.setRevision(head.getNextRevision());row.setSourceRevisionId(source);
        row.setContentJson(payload);row.setContentHash(hash(json.writeValueAsString(Arrays.asList(content,legacyBackgroundUrl))));row.setOperationId(operation);row.setOperationHash(intent);row.setPublisherId(CurrentUser.userId());row.setDefaultsRevision(defaultsRevision);row.setLegacyBackgroundUrl(legacyBackgroundUrl);
        revisions.insert(row);assets.snapshot(row.getId(),content,prepared);
        head.setPublishedRevisionId(row.getId());head.setNextRevision(Math.addExact(head.getNextRevision(),1));heads.updateById(head);
        return receipt(row);
    }
    private Receipt replay(String id,String intent) {
        var row=revisions.selectOne(scope().eq(BrandRevisionEntity::getOperationId,id).last("FOR SHARE"));
        if(row==null) return null;
        if(!Objects.equals(row.getPublisherId(),CurrentUser.userId()) || !Objects.equals(row.getOperationHash(),intent)) throw stale();
        return receipt(row);
    }
    private BrandHeadEntity lock() {
        var head=heads.selectOne(new LambdaQueryWrapper<BrandHeadEntity>().eq(BrandHeadEntity::getPortal,CurrentUser.portal()).last("FOR UPDATE"));
        if(head==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);return head;
    }
    private BrandRevisionEntity revision(long id) {
        var row=revisions.selectOne(scope().eq(BrandRevisionEntity::getId,id));if(row==null) throw new BizException(CommonErrorCode.DATA_NOT_FOUND);return row;
    }
    private LambdaQueryWrapper<BrandRevisionEntity> scope() { return new LambdaQueryWrapper<BrandRevisionEntity>().eq(BrandRevisionEntity::getPortal,CurrentUser.portal()); }
    private Revision view(BrandRevisionEntity row) { return new Revision(row.getId(),row.getRevision(),row.getSourceRevisionId(),row.getDefaultsRevision(),json.readValue(row.getContentJson(),BrandContent.class),row.getCreatedAt(),row.getLegacyBackgroundUrl()); }
    private Receipt receipt(BrandRevisionEntity row) { return new Receipt(row.getId(),row.getRevision(),row.getSourceRevisionId(),row.getOperationId()); }
    private void operation(String id) { if(id==null || !id.matches("[a-zA-Z0-9_-]{16,64}")) throw invalid(); }
    private void require(String permission) { if(!CurrentUser.require().permissions().contains(permission)) throw new AccessDeniedException("Brand operation permission required"); }
    private BizException invalid() { return new BizException(TenantErrorCode.BRAND_INVALID); }
    private BizException stale() { return new BizException(TenantErrorCode.STALE); }
    private String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
