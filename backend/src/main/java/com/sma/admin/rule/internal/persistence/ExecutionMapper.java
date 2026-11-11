package com.sma.admin.rule.internal.persistence;

@org.apache.ibatis.annotations.Mapper
public interface ExecutionMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<ExecutionEntity> {
    @org.apache.ibatis.annotations.Select("""
        <script>
        SELECT e.id,e.instance_id,e.instance_revision,e.release_revision,e.template_version_id,e.status,e.duration_micros,e.created_at
        FROM rule_execution e JOIN rule_asset a ON a.id=e.instance_id AND a.tenant_id=e.tenant_id AND a.portal=e.portal AND a.deleted=0
        LEFT JOIN rule_version v ON v.id=e.template_version_id AND v.tenant_id=e.tenant_id AND v.portal=e.portal
        WHERE e.tenant_id=#{tenant} AND e.portal=#{portal} AND e.deleted=0 AND e.created_at &gt;= #{q.from} AND e.created_at &lt; #{q.until}
        <if test="owner != null">AND a.created_by=#{owner}</if>
        <if test="q.instanceId != null">AND e.instance_id=#{q.instanceId}</if>
        <if test="q.templateId != null">AND v.asset_id=#{q.templateId}</if>
        <if test="q.templateVersionId != null">AND e.template_version_id=#{q.templateVersionId}</if>
        <if test="q.status != null">AND e.status=#{q.status}</if>
        ORDER BY e.id DESC LIMIT #{q.size} OFFSET #{offset}
        </script>
        """)
    java.util.List<ExecutionEntity> search(long tenant, String portal, Long owner, com.sma.admin.rule.RuleCommands.ExecutionSearch q, int offset);
    @org.apache.ibatis.annotations.Select("SELECT status, COUNT(*) AS total, AVG(duration_micros) AS averageMicros, MAX(duration_micros) AS maxMicros FROM rule_execution WHERE tenant_id=#{tenant} AND portal=#{portal} AND instance_id=#{instance} AND deleted=0 AND created_at >= #{from} AND created_at < #{until} GROUP BY status")
    java.util.List<java.util.Map<String, Object>> statistics(long tenant, String portal, long instance, java.time.Instant from, java.time.Instant until);
    @org.apache.ibatis.annotations.Delete("DELETE FROM rule_execution WHERE tenant_id=#{tenant} AND portal=#{portal} AND created_at < #{before} ORDER BY id LIMIT #{limit}")
    int purge(long tenant, String portal, java.time.Instant before, int limit);
}
