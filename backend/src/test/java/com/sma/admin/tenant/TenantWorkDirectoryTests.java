package com.sma.admin.tenant;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import com.sma.admin.common.security.*;
import com.sma.admin.support.*;
import com.sma.admin.tenant.internal.service.TenantDirectoryService;

@IntegrationTest @TestPropertySource(properties="sma.tenant.platform-admin-user-ids=991")
class TenantWorkDirectoryTests {
    @Autowired TenantWorkQuery jobs;@Autowired TenantDirectoryService directory;
    private static final SmaPrincipal OPERATOR=new SmaPrincipal(991,"platform",1,"admin",null,false,DataAccess.unrestricted(),Set.of(),"UTC");
    @Test void backgroundPagesAreBoundedAndIncludeInactiveTenantsForCleanup(){
        var tenant=Users.as(OPERATOR,()->directory.create(new TenantDirectoryService.TenantInput("job-"+UUID.randomUUID().toString().substring(0,8),"Jobs")));
        assertThat(jobs.page(tenant.id()-1,1)).containsExactly(new TenantWorkQuery.Entry(tenant.id(),false));
        Users.as(OPERATOR,()->{directory.changeStatus(tenant.id(),new TenantDirectoryService.StatusInput("ACTIVE",1));return null;});
        assertThat(jobs.page(tenant.id()-1,1)).containsExactly(new TenantWorkQuery.Entry(tenant.id(),true));
        assertThatThrownBy(()->jobs.page(0,101)).isInstanceOf(IllegalArgumentException.class);
        Users.as(OPERATOR,()->{assertThatThrownBy(()->jobs.page(0,10)).isInstanceOf(AccessDeniedException.class);return null;});
    }
}
