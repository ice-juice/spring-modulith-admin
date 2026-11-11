package com.sma.admin.support;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import com.sma.admin.common.security.SmaPrincipal;

/** Explicit test-only directory data for synthetic Users.as actors. Never changes production host/auth rules. */
public class TenantTestFixtures implements TestExecutionListener {
    @Override public void beforeTestMethod(TestContext context) {
        var profiles=AnnotatedElementUtils.findMergedAnnotation(context.getTestClass(),org.springframework.test.context.ActiveProfiles.class);
        if(profiles==null || !java.util.Arrays.asList(profiles.profiles()).contains("test")) return;
        if(context.getApplicationContext().getBeansOfType(com.sma.admin.common.security.TenantEntryQuery.class).isEmpty()) return;
        var jdbc=new JdbcTemplate(context.getApplicationContext().getBean(DataSource.class));
        Users.tenantFixtures(actor -> ensure(jdbc,actor));
    }
    @Override public void afterTestMethod(TestContext context) { Users.tenantFixtures(null); }

    static String host(SmaPrincipal actor) { return "t"+actor.tenantId()+"-"+actor.portal()+".test"; }
    private static void ensure(JdbcTemplate jdbc,SmaPrincipal actor) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_tenant WHERE id=?",Long.class,actor.tenantId())==0) jdbc.update("""
            INSERT INTO tenant_tenant (id,code,name,status,security_generation,created_at,created_by,updated_at,updated_by,deleted)
            VALUES (?,?,?,'ACTIVE',1,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0) ON DUPLICATE KEY UPDATE id=id
            """,actor.tenantId(),"fixture-"+actor.tenantId(),"Synthetic test tenant "+actor.tenantId());
        if(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_domain WHERE hostname=? AND deleted=0",Long.class,host(actor))==0) jdbc.update("""
            INSERT INTO tenant_domain (id,owner_tenant_id,hostname,portal,status,primary_domain,verification_status,tls_status,provenance,dns_status,verified_at,tls_valid_until,
                created_at,created_by,updated_at,updated_by,deleted)
            VALUES (?,?,?,?,'ACTIVE',0,'VERIFIED','READY','TEST','READY',UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
            ON DUPLICATE KEY UPDATE id=id
            """,com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),actor.tenantId(),host(actor),actor.portal());
    }
}
