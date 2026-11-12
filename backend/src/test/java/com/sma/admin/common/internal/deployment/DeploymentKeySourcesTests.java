package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.admin.auth.internal.service.AccountSecurityProperties;
import com.sma.admin.auth.internal.service.SecuritySecrets;
import com.sma.admin.common.deployment.DeploymentEvidence;
import com.sma.admin.common.internal.security.*;
import com.sma.admin.common.internal.service.RequestExecutionService;
import com.sma.admin.common.internal.persistence.*;
import com.sma.admin.openapi.internal.service.*;

class DeploymentKeySourcesTests {
    @TempDir Path directory;
    private static final DeploymentEvidence.Challenge CHALLENGE = new DeploymentEvidence.Challenge("a".repeat(64), "b".repeat(64), "c".repeat(64));
    private static byte[] key(int value) { var result = new byte[32]; Arrays.fill(result,(byte)value); return result; }
    private static String b64(int value) { return Base64.getEncoder().encodeToString(key(value)); }
    private SecuritySecrets auth(int old) throws Exception {
        var file=directory.resolve(UUID.randomUUID()+".auth");
        Files.writeString(file,"encryption.active=current\npepper.active=current\nencryption.current="+b64(1)+"\npepper.current="+b64(2)
                +(old<0?"":"\nencryption.old="+b64(old)+"\npepper.old="+b64(4)));
        return new SecuritySecrets(new AccountSecurityProperties(file.toString(),"SMA"));
    }
    private RequestExecutionService requests(String previous) {
        return new RequestExecutionService(mock(RequestExecutionMapper.class),mock(RequestMutexMapper.class),mock(RequestBudgetMapper.class),
                mock(org.springframework.transaction.PlatformTransactionManager.class),
                new RequestGovernanceProperties(b64(5),"current",previous,null,null));
    }

    @Test void authAndRequestEvidenceIncludesRetainedKeysUsedByActualServices() throws Exception {
        var first=auth(3); var same=auth(3);
        assertThat(first.collect(CHALLENGE)).isEqualTo(same.collect(CHALLENGE));
        assertThat(first.collect(CHALLENGE)).isNotEqualTo(auth(6).collect(CHALLENGE)).isNotEqualTo(auth(-1).collect(CHALLENGE));
        var secret=first.encrypt("private-value","tenant:purpose"); first.collect(CHALLENGE);
        assertThat(same.decrypt(secret,"tenant:purpose")).isEqualTo("private-value");
        assertThat(new SecuritySecrets(new AccountSecurityProperties("",null)).collect(CHALLENGE).available()).isFalse();
        assertThat(requests("old="+b64(6)).collect(CHALLENGE)).isEqualTo(requests("old="+b64(6)).collect(CHALLENGE))
                .isNotEqualTo(requests("old="+b64(7)).collect(CHALLENGE)).isNotEqualTo(requests("").collect(CHALLENGE));
    }

    @Test void tenantCredentialEvidenceUsesLoadedRingAndBecomesUnavailableAfterDestroy() throws Exception {
        var file=directory.resolve("credentials.keys"); Files.writeString(file,"active=current\nkey.current="+b64(6)+"\nkey.old="+b64(7));
        var first=new TenantCredentialCrypto(new TenantCredentialProperties(file.toString()));
        var initial=first.collect(CHALLENGE);
        Files.writeString(file,"active=current\nkey.current="+b64(6)+"\nkey.old="+b64(8));
        assertThat(first.collect(CHALLENGE)).isEqualTo(initial);
        var second=new TenantCredentialCrypto(new TenantCredentialProperties(file.toString()));
        assertThat(second.collect(CHALLENGE)).isNotEqualTo(initial);
        first.destroy(); second.destroy(); assertThat(first.collect(CHALLENGE).available()).isFalse();
        assertThat(new TenantCredentialCrypto(new TenantCredentialProperties("")).collect(CHALLENGE).available()).isFalse();
    }

    @Test void openapiProbeNeverCreatesFilesAndBindsTheKeyActuallyUsedForEncryption() throws Exception {
        var absent=directory.resolve("absent/openapi.key");
        var missing=new SecretCipher(new OpenapiProperties(null,absent.toString()));
        assertThat(missing.collect(CHALLENGE).available()).isFalse(); assertThat(absent.getParent()).doesNotExist();
        var file=directory.resolve("existing.key"); Files.write(file,key(9));
        var first=new SecretCipher(new OpenapiProperties(null,file.toString()));
        var initial=first.collect(CHALLENGE); assertThat(initial.available()).isTrue();
        var explicit=new SecretCipher(new OpenapiProperties(b64(9),directory.resolve("unused.key").toString()));
        assertThat(explicit.collect(CHALLENGE)).isEqualTo(initial);
        String encrypted=first.encrypt("private-secret","app");
        assertThat(explicit.decrypt(encrypted,"app")).isEqualTo("private-secret");
        Files.write(file,key(10)); assertThat(first.collect(CHALLENGE)).isEqualTo(initial);
        assertThat(new SecretCipher(new OpenapiProperties(null,file.toString())).collect(CHALLENGE)).isNotEqualTo(initial);
        Files.write(file,new byte[33]); assertThat(new SecretCipher(new OpenapiProperties(null,file.toString())).collect(CHALLENGE).available()).isFalse();
        missing.encrypt("local-development","app"); assertThat(absent).exists();
        assertThat(missing.collect(CHALLENGE).available()).isFalse(); // Legacy single-node generation is not deployment proof.
    }

    @Test void registryUsesRealOwnersAndCannotDeclareMissingCriticalRingReady() throws Exception {
        var file=directory.resolve("complete.keys"); Files.writeString(file,"active=current\nkey.current="+b64(6));
        var tenant=new TenantCredentialCrypto(new TenantCredentialProperties(file.toString()));
        var auth=auth(3); var requests=requests("old="+b64(6));
        var openapi=new SecretCipher(new OpenapiProperties(b64(9),null));
        var first=new DeploymentEvidenceRegistry(List.of(auth,requests,tenant,openapi)).collect(CHALLENGE);
        var second=new DeploymentEvidenceRegistry(List.of(openapi,tenant,requests,auth)).collect(CHALLENGE);
        assertThat(first.ready()).isFalse(); assertThat(first.unavailable()).contains("common.cache-policy", "common.host-policy");
        assertThat(first.unavailable()).isEqualTo(second.unavailable()); // Key-only registries cannot skip configuration evidence.
        assertThat(new DeploymentEvidenceRegistry(List.of(auth,requests,openapi)).collect(CHALLENGE).ready()).isFalse();
        tenant.destroy(); assertThat(new DeploymentEvidenceRegistry(List.of(auth,requests,tenant,openapi)).collect(CHALLENGE).ready()).isFalse();
    }
}
