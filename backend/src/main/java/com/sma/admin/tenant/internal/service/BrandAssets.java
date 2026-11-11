package com.sma.admin.tenant.internal.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.file.ImagePublication;
import com.sma.admin.tenant.BrandContent;
import com.sma.admin.tenant.internal.persistence.*;

@Service
public class BrandAssets {
    private final ImagePublication images;
    private final BrandReferenceMapper references;
    private final BrandAssetMapper assets;
    private final BrandWorkBudget budget;
    public BrandAssets(ImagePublication images,BrandReferenceMapper references,BrandAssetMapper assets,BrandWorkBudget budget) { this.images=images;this.references=references;this.assets=assets;this.budget=budget; }
    @Transactional(propagation=Propagation.MANDATORY)
    public Map<Long,ImagePublication.Image> prepare(BrandContent content) {
        if(!content.assets().isEmpty()) budget.enter();
        Map<Long,ImagePublication.Image> snapshots=new LinkedHashMap<>();
        content.assets().values().stream().distinct().sorted().forEach(id->snapshots.put(id,images.prepare(id)));
        return snapshots;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void replaceDraft(BrandContent content) {
        prepare(content);references.clearDraft(CurrentUser.portal());reference("draft",content);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void snapshot(long revision,BrandContent content,Map<Long,ImagePublication.Image> prepared) {
        for(var entry:content.assets().entrySet()) {
            var image=prepared.get(entry.getValue());var row=new BrandAssetEntity();
            row.setPortal(CurrentUser.portal());row.setRevisionId(revision);row.setSlot(entry.getKey().name());row.setSourceFileId(entry.getValue());
            row.setImageBytes(image.bytes());row.setChecksum(image.checksum());row.setWidth(image.width());row.setHeight(image.height());assets.insert(row);
        }
        reference("revision-"+revision,content);
    }
    private void reference(String key,BrandContent content) {
        content.assets().forEach((slot,id)->{var row=new BrandReferenceEntity();row.setPortal(CurrentUser.portal());row.setReferenceKey(key);row.setSlot(slot.name());row.setFileId(id);references.insert(row);});
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Map<Long,ImagePublication.Image> existing(long revision,BrandContent content) {
        if(!content.assets().isEmpty()) budget.enter();
        Map<Long,ImagePublication.Image> result=new LinkedHashMap<>();
        var rows=assets.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BrandAssetEntity>()
                .eq(BrandAssetEntity::getPortal,CurrentUser.portal()).eq(BrandAssetEntity::getRevisionId,revision));
        for(var row:rows) result.put(row.getSourceFileId(),new ImagePublication.Image(row.getImageBytes(),"image/png",row.getChecksum(),row.getWidth(),row.getHeight()));
        if(!result.keySet().containsAll(content.assets().values()) || rows.size()!=content.assets().size())
            throw new com.sma.admin.common.BizException(com.sma.admin.tenant.TenantErrorCode.BRAND_INVALID);
        return result;
    }
}
