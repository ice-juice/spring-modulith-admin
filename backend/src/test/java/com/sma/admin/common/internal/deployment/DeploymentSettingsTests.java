package com.sma.admin.common.internal.deployment;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import com.sma.admin.common.cache.*;
import com.sma.admin.common.deployment.*;
import com.sma.admin.common.internal.cache.*;
import com.sma.admin.common.internal.security.*;
import com.sma.admin.auth.internal.service.*;
import com.sma.admin.file.internal.service.*;
import com.sma.admin.identity.internal.service.*;
import com.sma.admin.mail.internal.service.*;
import com.sma.admin.message.internal.service.*;

class DeploymentSettingsTests {
    @TempDir Path directory;
    private static final DeploymentEvidence.Challenge C = new DeploymentEvidence.Challenge("a".repeat(64),"b".repeat(64),"c".repeat(64));
    private static String proof(DeploymentEvidenceSource source){return source.collect(C).digest();}
    private static final IdentitySessionProperties SESSIONS=new IdentitySessionProperties(0);
    private static final IdentityHttpProperties HTTP=new IdentityHttpProperties(null,null);
    private static final IdentityLoginProperties LOGIN=new IdentityLoginProperties(null,null,null,null,null,null,null,null,null);
    private static final IdentityEmailProperties EMAIL=new IdentityEmailProperties(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);

    @Test void canonicalCollectionsAndFieldBoundariesCannotCollideOrOverwrite(){
        assertThat(new DeploymentFields().values("values",List.of("b","a","a")).proof("common.fields",C))
                .isEqualTo(new DeploymentFields().values("values",List.of("a","b")).proof("common.fields",C))
                .isNotEqualTo(new DeploymentFields().values("values",List.of("a,b")).proof("common.fields",C));
        assertThatThrownBy(()->new DeploymentFields().text("a","one").text("a","two")).isInstanceOf(IllegalArgumentException.class);
        var fields=new DeploymentFields();for(int i=0;i<512;i++)fields.number("f"+i,i);
        assertThatThrownBy(()->fields.number("overflow",0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void hostPolicyTracksSecurityBoundsButDoesNotDuplicateKeyMaterial(){
        var browser=new BrowserRequestProperties(Set.of("https://example.test"));
        var ingress=new TenantIngressProperties(Set.of("127.0.0.1"));
        var schema=new SchemaValidationConfiguration.Properties(SchemaValidationConfiguration.Properties.SchemaMode.VALIDATE);
        var requests=new RequestGovernanceProperties("private-a","id-a","old-private-a",null,null);
        var baseline=proof(new HostPolicyEvidence(schema,browser,requests,ingress));
        assertThat(proof(new HostPolicyEvidence(schema,browser,requests,ingress,new DeploymentTrafficProperties(true)))).isNotEqualTo(baseline);
        assertThat(proof(new HostPolicyEvidence(schema,browser,new RequestGovernanceProperties("private-b","id-b","old-private-b",null,null),ingress))).isEqualTo(baseline);
        assertThat(proof(new HostPolicyEvidence(new SchemaValidationConfiguration.Properties(null),browser,requests,ingress))).isNotEqualTo(baseline);
        assertThat(proof(new HostPolicyEvidence(schema,new BrowserRequestProperties(Set.of("https://other.test")),requests,ingress))).isNotEqualTo(baseline);
        assertThat(proof(new HostPolicyEvidence(schema,browser,requests,new TenantIngressProperties(Set.of("127.0.0.2"))))).isNotEqualTo(baseline);
        assertThat(proof(new HostPolicyEvidence(schema,browser,new RequestGovernanceProperties("private-a","id-a","",100001L,8),ingress))).isNotEqualTo(baseline);
        assertThat(proof(new HostPolicyEvidence(schema,new BrowserRequestProperties(browser.allowedOrigins(),4096,16,4),requests,ingress))).isNotEqualTo(baseline);
    }

    @Test void cacheProofTracksNamespaceEffectivePoliciesCapabilitiesAndRegistrationSchema(){
        var local=new CacheProperties(null,"one",null,null,null,null,Map.of());
        var baseline=proof(cache(local,descriptor(1),null,false));
        assertThat(proof(cache(new CacheProperties(null,"two",null,null,null,null,Map.of()),descriptor(1),null,false))).isNotEqualTo(baseline);
        assertThat(proof(cache(local,descriptor(2),null,false))).isNotEqualTo(baseline);
        assertThat(proof(cache(local,descriptor(1),null,true))).isNotEqualTo(baseline);
        var override=new CacheProperties.RegionOverride(Duration.ofMinutes(1),null,null,null,null);
        assertThat(proof(cache(new CacheProperties(null,"one",null,null,null,null,Map.of("common.example",override)),descriptor(1),null,false))).isNotEqualTo(baseline);
        var explicitDefault=new CacheProperties.RegionOverride(Duration.ofMinutes(5),null,null,null,null);
        assertThat(proof(cache(new CacheProperties(null,"one",null,null,null,null,Map.of("common.example",explicitDefault)),descriptor(1),null,false))).isEqualTo(baseline);
    }

    @Test void redisSeedOrderAndPerNodeAclCredentialsDoNotChangeSemanticTopologyProof(){
        var settings=new CacheProperties(CacheProperties.Provider.REDIS,"test-redis",null,null,null,null,Map.of());
        var a=new CacheRedisProperties(null,null,0,"user-a","private-a",null,null,null,null,new CacheRedisProperties.Cluster(List.of("NODE-B:6379","node-a:6379"),3));
        var b=new CacheRedisProperties(null,null,0,"user-b","private-b",null,null,null,null,new CacheRedisProperties.Cluster(List.of("node-a:6379","node-b:6379"),3));
        var baseline=proof(cache(settings,descriptor(1),a,false));
        assertThat(proof(cache(settings,descriptor(1),b,false))).isEqualTo(baseline);
        var other=new CacheRedisProperties(null,null,0,null,null,null,null,new CacheRedisProperties.Ssl(true),null,b.cluster());
        assertThat(proof(cache(settings,descriptor(1),other,false))).isNotEqualTo(baseline);
        assertThat(cache(settings,descriptor(1),null,false).collect(C).available()).isFalse();
        var standalone=new CacheRedisProperties("localhost",6379,0,null,null,null,null,null,null,null);
        assertThat(proof(cache(settings,descriptor(1),standalone,false)))
                .isNotEqualTo(proof(cache(settings,descriptor(1),new CacheRedisProperties("localhost",6379,1,null,null,null,null,null,null,null),false)));
    }

    @Test void authenticationSettingsAndSocketSourcesAreBoundButKeyPathsAreExcluded() throws Exception {
        var auth=new AuthProperties(null,null,false,null,0);
        changingEveryComponentChangesProof(auth,new AuthProperties(Duration.ofHours(9),Duration.ofDays(8),true,Duration.ofMinutes(2),3),
                p->new AuthPolicyEvidence(p,new AccountSecurityProperties("one-path","Issuer")));
        assertThat(proof(new AuthPolicyEvidence(auth,new AccountSecurityProperties("one-path","Issuer"))))
                .isEqualTo(proof(new AuthPolicyEvidence(auth,new AccountSecurityProperties("different-path","Issuer"))))
                .isNotEqualTo(proof(new AuthPolicyEvidence(auth,new AccountSecurityProperties("one-path","Other issuer"))));
        var a=new SocketProperties(null,List.of("https://a.test","https://b.test"),100);
        assertThat(proof(new SocketPolicyEvidence(a))).isEqualTo(proof(new SocketPolicyEvidence(new SocketProperties(null,List.of("https://b.test","https://a.test"),100))))
                .isNotEqualTo(proof(new SocketPolicyEvidence(new SocketProperties(Duration.ofSeconds(31),a.allowedOrigins(),100))));
    }

    @Test void everyReviewedIdentityBudgetFieldAffectsEvidence() throws Exception {
        changingEveryComponentChangesProof(SESSIONS,new IdentitySessionProperties(11),p->new IdentityPolicyEvidence(p,HTTP,LOGIN,EMAIL));
        changingEveryComponentChangesProof(HTTP,new IdentityHttpProperties(65,65537),p->new IdentityPolicyEvidence(SESSIONS,p,LOGIN,EMAIL));
        changingEveryComponentChangesProof(LOGIN,new IdentityLoginProperties(Duration.ofMinutes(4),Duration.ofDays(2),1001,10001,2001,201,5001,21,11),
                p->new IdentityPolicyEvidence(SESSIONS,HTTP,p,EMAIL));
        changingEveryComponentChangesProof(EMAIL,new IdentityEmailProperties(Duration.ofMinutes(4),Duration.ofSeconds(90),Duration.ofDays(2),1001,10001,5001,101,2001,21,6,11,501,20001,31,6),
                p->new IdentityPolicyEvidence(SESSIONS,HTTP,LOGIN,p));
    }

    @Test void filePathsMayDifferButResourceDeclarationsAndEveryUploadLimitAreBound() throws Exception {
        var tasks=new UploadTaskProperties(null,null,null,null,null,null,null,null,null,null);
        var transfer=new LocalTransferProperties(null,null,null);
        var a=new FileProperties(directory.resolve("node-a").toString(),0);var b=new FileProperties(directory.resolve("node-b").toString(),0);
        try(var one=new FileClusterStorage(a,new StorageSettings(Map.of("default",directory.resolve("managed-a").toString())),new FileClusterProperties(Set.of("file.legacy","file.local.default"),null));
            var two=new FileClusterStorage(b,new StorageSettings(Map.of("default",directory.resolve("managed-b").toString())),new FileClusterProperties(Set.of("file.local.default","file.legacy"),null));
            var undeclared=new FileClusterStorage(a,new StorageSettings(Map.of("default",directory.resolve("managed-a").toString())),new FileClusterProperties(Set.of(),null))){
            var baseline=proof(new FilePolicyEvidence(a,one,tasks,transfer));
            assertThat(proof(new FilePolicyEvidence(b,two,tasks,transfer))).isEqualTo(baseline);
            assertThat(proof(new FilePolicyEvidence(a,undeclared,tasks,transfer))).isNotEqualTo(baseline);
            changingEveryComponentChangesProof(tasks,new UploadTaskProperties(Duration.ofHours(23),Duration.ofDays(8),tasks.maximumReservedBytes()+1,101,10001,31,1001,17L*1024*1024,9999,4),p->new FilePolicyEvidence(a,one,p,transfer));
            assertThat(directory.resolve("node-a")).doesNotExist(); assertThat(directory.resolve("managed-a")).doesNotExist();
        }
    }

    @Test void mailQueueBudgetsAndEgressAllowlistCannotSilentlyDiffer() throws Exception {
        var work=new MailWorkProperties(0,0,null);
        var routes=List.of(new SmtpProperties.Route("mail-a.test",Set.of(465,587),Set.of("10.0.0.1")),new SmtpProperties.Route("mail-b.test",Set.of(587),Set.of()));
        var smtp=new SmtpProperties(routes,null,null,null,null,0);
        changingEveryComponentChangesProof(work,new MailWorkProperties(10001,6,Duration.ofMinutes(3)),p->new MailPolicyEvidence(p,smtp));
        var baseline=proof(new MailPolicyEvidence(work,smtp));
        assertThat(proof(new MailPolicyEvidence(work,new SmtpProperties(routes.reversed(),null,null,null,null,0)))).isEqualTo(baseline);
        assertThat(proof(new MailPolicyEvidence(work,new SmtpProperties(List.of(routes.getFirst()),null,null,null,null,0)))).isNotEqualTo(baseline);
        assertThat(proof(new MailPolicyEvidence(work,new SmtpProperties(List.of(new SmtpProperties.Route("mail-a.test",Set.of(465),Set.of("10.0.0.1")),routes.getLast()),null,null,null,null,0)))).isNotEqualTo(baseline);
    }

    private static CacheDescriptor descriptor(int version){return new CacheDescriptor("common.example","common","common.example","test","test",CacheDescriptor.Kind.DATA,
            CacheDescriptor.Scope.TENANT,version,CachePolicy.defaults(),Set.of(CacheDescriptor.Operation.CLEAR),Set.of("write"),"common:example:manage");}
    private static CachePolicyEvidence cache(CacheProperties settings,CacheDescriptor descriptor,CacheRedisProperties redis,boolean warmup){
        var beans=new DefaultListableBeanFactory();if(redis!=null)beans.registerSingleton("redis",redis);
        return new CachePolicyEvidence(settings,new CacheRegistry(List.of(()->List.of(descriptor))),new CacheResilienceProperties(null,null,null,null,null),
                new CacheWarmupProperties(warmup),new CacheResourceTaskProperties(false),beans.getBeanProvider(CacheRedisProperties.class));
    }
    /** Test-side coverage of the explicitly reviewed record fields; production does not reflect or serialize records. */
    private static <T extends Record> void changingEveryComponentChangesProof(T baseline,T alternatives,Function<T,DeploymentEvidenceSource> source) throws Exception {
        var components=baseline.getClass().getRecordComponents();var types=Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
        var constructor=baseline.getClass().getDeclaredConstructor(types);var initial=proof(source.apply(baseline));
        for(int changed=0;changed<components.length;changed++){
            var values=new Object[components.length];for(int i=0;i<components.length;i++)values[i]=components[i].getAccessor().invoke(i==changed?alternatives:baseline);
            @SuppressWarnings("unchecked") var candidate=(T)constructor.newInstance(values);
            assertThat(proof(source.apply(candidate))).as("deployment field %s.%s",baseline.getClass().getSimpleName(),components[changed].getName()).isNotEqualTo(initial);
        }
    }
}
