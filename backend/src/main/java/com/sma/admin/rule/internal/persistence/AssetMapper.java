package com.sma.admin.rule.internal.persistence;

@org.apache.ibatis.annotations.Mapper
public interface AssetMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<AssetEntity> {
    @org.apache.ibatis.annotations.Select("""
        <script>
        SELECT a.* FROM rule_asset a WHERE a.deleted=0 AND a.portal=#{portal} AND a.kind='INSTANCE'
        AND (a.reference_id=#{templateId} OR EXISTS (
          SELECT 1 FROM rule_version iv JOIN rule_version tv ON tv.id=iv.dependency_id
          WHERE iv.id=a.active_version_id AND iv.deleted=0 AND tv.deleted=0
          AND iv.portal=#{portal} AND tv.portal=#{portal} AND iv.kind='INSTANCE' AND tv.asset_id=#{templateId}
        ))
        <if test="owner != null">AND a.created_by=#{owner}</if>
        ORDER BY a.id LIMIT #{offset},#{size}
        </script>
        """)
    java.util.List<AssetEntity> impacted(long templateId, String portal, Long owner, int offset, int size);
}
