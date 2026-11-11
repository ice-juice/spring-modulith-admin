package com.sma.admin.rule.internal.persistence;

@org.apache.ibatis.annotations.Mapper
public interface TestRunMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<TestRunEntity> {
    @org.apache.ibatis.annotations.Delete("DELETE FROM rule_testrun WHERE tenant_id=#{tenant} AND portal=#{portal} AND created_at < #{before} AND NOT EXISTS (SELECT 1 FROM rule_version v WHERE v.test_run_id=rule_testrun.id AND v.tenant_id=rule_testrun.tenant_id) ORDER BY id LIMIT #{limit}")
    int purgeUnreferenced(long tenant, String portal, java.time.Instant before, int limit);
}
