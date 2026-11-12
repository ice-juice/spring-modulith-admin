package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.internal.security.*;

class RequestSourceEvidenceTests {
    private String digest(RequestSourceProperties source){
        return new HostPolicyEvidence(new SchemaValidationConfiguration.Properties(null),new BrowserRequestProperties(Set.of()),
                new RequestGovernanceProperties(null,null,null,null,null),new TenantIngressProperties(Set.of()),new DeploymentTrafficProperties(false),source)
                .collect(new DeploymentEvidence.Challenge("a".repeat(64),"b".repeat(64),"c".repeat(64))).digest();
    }
    @Test void actualSourcePolicyChangesInvalidateEvidenceAndCanonicalNetworksRemainStable(){
        String baseline=digest(new RequestSourceProperties(Set.of("192.0.2.1","2001:db8::/32"),16,2048));
        assertThat(digest(new RequestSourceProperties(Set.of("2001:DB8::/32","192.0.2.1/32"),16,2048))).isEqualTo(baseline);
        assertThat(digest(new RequestSourceProperties(Set.of("192.0.2.0/24","2001:db8::/32"),16,2048))).isNotEqualTo(baseline);
        assertThat(digest(new RequestSourceProperties(Set.of("192.0.2.1","2001:db8::/32"),8,2048))).isNotEqualTo(baseline);
        assertThat(digest(new RequestSourceProperties(Set.of("192.0.2.1","2001:db8::/32"),16,512))).isNotEqualTo(baseline);
    }
}
