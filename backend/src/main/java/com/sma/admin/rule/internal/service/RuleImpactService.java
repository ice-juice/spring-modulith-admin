package com.sma.admin.rule.internal.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sma.admin.common.security.CurrentUser;
import com.sma.admin.rule.*;
import com.sma.admin.rule.RuleCommands.*;
import com.sma.admin.rule.internal.persistence.*;

/** Bounded dependency scan; a truncated report must never be treated as proof of no impact. */
@Service
@Transactional(readOnly = true)
public class RuleImpactService {
    private final RuleService rules;
    private final AssetMapper assets;
    private final VersionMapper versions;
    private final RuleJson json;
    public RuleImpactService(RuleService rules, AssetMapper assets, VersionMapper versions, RuleJson json) {
        this.rules = rules; this.assets = assets; this.versions = versions; this.json = json;
    }
    public DependencyImpact impact(long templateId) {
        rules.get(templateId, "TEMPLATE");
        var query = new LambdaQueryWrapper<AssetEntity>().eq(AssetEntity::getPortal, CurrentUser.portal())
                .in(AssetEntity::getKind, List.of("TEMPLATE", "INSTANCE"));
        CurrentUser.optional().ifPresent(user -> {
            if (!user.superAdmin() && !user.dataAccess().all()) query.eq(AssetEntity::getCreatedBy, user.dataAccess().self() ? user.userId() : -1L);
        });
        var candidates = assets.selectList(query.orderByAsc(AssetEntity::getId).last("LIMIT 501"));
        boolean[] truncated = {candidates.size() > 500}; var found = new ArrayList<ImpactAsset>();
        for (var asset : candidates.stream().limit(500).toList()) {
            if (asset.getId() == templateId) continue;
            boolean hit = content(asset.getKind(), asset.getDraftJson(), templateId, new HashSet<>(), 0, truncated);
            if (asset.getActiveVersionId() != null) hit |= depends(asset.getActiveVersionId(), templateId, new HashSet<>(), 0, truncated);
            if (hit) found.add(new ImpactAsset(asset.getId(), asset.getKind(), asset.getCode(), asset.getState()));
        }
        return new DependencyImpact(templateId, List.copyOf(found), Math.min(500, candidates.size()), truncated[0]);
    }
    private boolean depends(long versionId, long target, Set<Long> visited, int depth, boolean[] truncated) {
        if (depth > 8 || visited.size() >= 64) { truncated[0] = true; return false; }
        if (!visited.add(versionId)) return false;
        var version = versions.selectById(versionId);
        if (version == null || !CurrentUser.portal().equals(version.getPortal())) { truncated[0] = true; return false; }
        if (version.getAssetId() == target) return true;
        if (!json.hash(version.getContentJson()).equals(version.getContentHash())) { truncated[0] = true; return false; }
        return content(version.getKind(), version.getContentJson(), target, visited, depth + 1, truncated);
    }
    private boolean content(String kind, String content, long target, Set<Long> visited, int depth, boolean[] truncated) {
        try {
            if (kind.equals("INSTANCE")) return depends(json.read(content, Instance.class).templateVersionId(), target, visited, depth, truncated);
            var definition = json.read(content, RuleTemplateDocument.class).definition();
            if (definition.flow() == null) return false;
            boolean hit = false;
            for (var node : definition.flow().nodes()) if (node.subRuleVersionId() != null)
                hit |= depends(node.subRuleVersionId(), target, visited, depth, truncated);
            return hit;
        } catch (RuleFailure ex) { truncated[0] = true; return false; }
    }
}
