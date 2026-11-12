package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.internal.cache.*;
import com.sma.admin.common.internal.security.*;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.file.internal.service.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.message.internal.service.SocketProperties;
import com.sma.admin.tenant.BrandContent;
import com.sma.admin.tenant.internal.service.*;

/** New fields in a reviewed type require an explicit include/exclude decision, even with compatibility constructors. */
class DeploymentFieldInventoryTests {
    @Test void reviewedConfigurationShapesCannotGrowSilently() {
        fields(AuthProperties.class,"sessionTtl rememberTtl singleTerminal autoLockAfter passwordNoticeDays");
        fields(AccountSecurityProperties.class,"keyFile issuer"); // Key paths are deliberately excluded; actual key evidence is separate.
        fields(BrowserRequestProperties.class,"allowedOrigins maximumJsonBytes maximumConcurrentJson maximumConcurrentAnonymousJson");
        fields(RequestGovernanceProperties.class,"fingerprintKey fingerprintKeyId previousFingerprintKeys maxRecordsPerTenant maxConcurrent minimumWriteInterval");
        fields(TenantIngressProperties.class,"trustedProxyAddresses");
        fields(RequestSourceProperties.class,"trustedProxies maximumHops maximumHeaderCharacters");
        fields(com.sma.admin.defense.internal.service.DefenseStorageProperties.class,"maximumPlatformRecords maximumTenantRecords maximumTenantTotalRecords retention cleanupBatch");
        fields(com.sma.admin.defense.internal.service.DefenseCollectionProperties.class,"enabled directory keyFile memoryRecords diskRecords diskBytes perSecond batch retryDelay scope");
        fields(com.sma.admin.defense.internal.service.DefenseMaintenanceProperties.class,"enabled interval tenantBatch");
        fields(SchemaValidationConfiguration.Properties.class,"schemaMode");
        fields(DeploymentTrafficProperties.class,"enabled"); // Compared in host policy; restart-only.
        fields(CacheProperties.class,"provider namespace deploymentMode maxBytes maxScopes ttlJitter regions defaults");
        fields(CacheProperties.RegionOverride.class,"ttl negativeTtl maxEntries maxBytes maxEntryBytes");
        fields(CacheRedisProperties.class,"host port database username password timeout connectTimeout ssl sentinel cluster"); // ACL values excluded.
        fields(CacheRedisProperties.Ssl.class,"enabled");fields(CacheRedisProperties.Sentinel.class,"master nodes username password");fields(CacheRedisProperties.Cluster.class,"nodes maxRedirects");
        fields(CacheResilienceProperties.class,"maxConcurrentLoads maxConcurrentPerRegion lockWait failureThreshold retryDelay");
        fields(CacheWarmupProperties.class,"enabled");fields(CacheResourceTaskProperties.class,"enabled");
        fields(SocketProperties.class,"ticketTtl allowedOrigins maxConnections");
        fields(IdentitySessionProperties.class,"maximumPerSubject");fields(IdentityHttpProperties.class,"maximumConcurrent maximumJsonBytes");
        fields(IdentityLoginProperties.class,"lifetime retention maximumOutstanding maximumRetained maximumRateKeys tenantPerMinute tenantPerDay clientPerMinute sourcePerMinute");
        fields(IdentityEmailProperties.class,"lifetime grantLifetime retention maximumOutstanding maximumRetained maximumRateKeys tenantPerMinute tenantPerDay clientPerMinute sourcePerMinute targetPerDay guessesPerMinute guessesPerDay sourceGuessesPerMinute maximumFailures");
        fields(FileProperties.class,"directory maxBytes");fields(StorageSettings.class,"localRoots"); // Compare resource IDs, never private paths.
        fields(FileClusterProperties.class,"sharedResources probeTimeout"); // Probe deadline is node-local, physical I/O is a separate gate.
        fields(UploadTaskProperties.class,"lifetime retention maximumReservedBytes maximumActive maximumRetained perMinute perDay partBytes maximumParts maximumConcurrentParts");
        fields(LocalTransferProperties.class,"maximumConcurrent deadline minimumFreeBytes");
        fields(MailWorkProperties.class,"maximumQueuedPerProfile maximumAttempts leaseDuration");
        fields(SmtpProperties.class,"routes connectTimeout readTimeout writeTimeout operationTimeout maximumConcurrent");fields(SmtpProperties.Route.class,"host ports approvedPrivateAddresses");
        fields(TenantPlatformProperties.class,"platformTenantId platformAdminUserIds bootstrapHosts");
        fields(DomainVerificationProperties.class,"ingressAddresses dnsTimeout connectTimeout totalTimeout");fields(BrandDefaultsProperties.class,"revision content");
        fields(BrandContent.class,"schemaVersion identity login legal links appearance assets overrides"); // Defaults reject assets/overrides.
        fields(BrandContent.Identity.class,"systemName shortName englishName");fields(BrandContent.Login.class,"templateId title description backgroundBlur");fields(BrandContent.Text.class,"zhCN enUS");
        fields(BrandContent.Legal.class,"copyrightOwner copyrightStartYear copyrightUrl icpRecord icpUrl policeRecord policeUrl");
        fields(BrandContent.Links.class,"website help support privacy agreement");fields(BrandContent.Appearance.class,"primaryColor defaultSkin allowPersonalSkin");
    }
    private static void fields(Class<?> type,String names) {
        assertThat(Arrays.stream(type.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList())
                .as("Explicit deployment evidence review for %s",type.getSimpleName()).containsExactlyInAnyOrder(names.split(" "));
    }
}
