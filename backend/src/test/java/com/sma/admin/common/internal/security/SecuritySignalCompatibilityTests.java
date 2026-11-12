package com.sma.admin.common.internal.security;

import static org.assertj.core.api.Assertions.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.admin.common.security.*;
import tools.jackson.databind.json.JsonMapper;

class SecuritySignalCompatibilityTests {
    @TempDir Path temp;
    private final JsonMapper json=JsonMapper.builder().build();
    record OldSignal(UUID eventId,UUID correlationId,Instant occurredAt,SecuritySignal.Fact fact,
            SecuritySignal.Attribution attribution,RequestSource source) {}
    enum ClosedOldFact { AUTHORIZATION_REJECTED }
    record ClosedOldSignal(UUID eventId,UUID correlationId,Instant occurredAt,ClosedOldFact fact,
            SecuritySignal.Attribution attribution,RequestSource source) {}
    private SecuritySignal legacy(){return new SecuritySignal(UUID.randomUUID(),UUID.randomUUID(),Instant.parse("2026-11-12T00:00:00Z"),
            SecuritySignal.Fact.AUTHORIZATION_REJECTED,new SecuritySignal.Attribution(SecuritySignal.Scope.TENANT,SecuritySignal.Identity.ADMIN_USER,91L,"77","admin",null,null,null),
            new RequestSource("192.0.2.1","192.0.2.1",RequestSource.Basis.DIRECT,0));}
    @Test void absentMetadataPreservesOldPayloadBytesAndStrictOldReader(){
        var signal=legacy();var old=new OldSignal(signal.eventId(),signal.correlationId(),signal.occurredAt(),signal.fact(),signal.attribution(),signal.source());
        byte[] oldBytes=json.writeValueAsBytes(old),newBytes=json.writeValueAsBytes(signal);
        assertThat(newBytes).isEqualTo(oldBytes);assertThat(json.readValue(oldBytes,SecuritySignal.class)).isEqualTo(signal);
        assertThat(json.readValue(newBytes,OldSignal.class)).isEqualTo(old);assertThat(json.readTree(newBytes).has("metadata")).isFalse();
    }
    @Test void oldCompiledConsumerLinksToCurrentSixArgumentConstructorAndAccessors()throws Exception{
        var old=temp.resolve("com/sma/admin/common/security/SecuritySignal.java");Files.createDirectories(old.getParent());
        Files.writeString(old,"""
            package com.sma.admin.common.security;
            public record SecuritySignal(java.util.UUID eventId,java.util.UUID correlationId,java.time.Instant occurredAt,
                Fact fact,Attribution attribution,RequestSource source){
              public enum Fact { AUTHORIZATION_REJECTED }
              public record Attribution(){}
            }
            """);
        var client=temp.resolve("LegacySignalClient.java");Files.writeString(client,"""
            import com.sma.admin.common.security.SecuritySignal;
            public class LegacySignalClient {
              public static SecuritySignal copy(SecuritySignal input){
                return new SecuritySignal(input.eventId(),input.correlationId(),input.occurredAt(),input.fact(),input.attribution(),input.source());
              }
            }
            """);
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,null,null,"-proc:none","-classpath",System.getProperty("java.class.path"),
                "-d",temp.toString(),old.toString(),client.toString())).isZero();
        try(var loader=new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()},SecuritySignal.class.getClassLoader())){
            var input=legacy();assertThat(loader.loadClass("LegacySignalClient").getMethod("copy",SecuritySignal.class).invoke(null,input)).isEqualTo(input);
        }
    }
    @Test void maximumMetadataAndIdentityStayWithinBothStorageAndSpoolBudget(){
        var observer=new SecuritySignal.Observer("A".repeat(64),"n".repeat(48),UUID.randomUUID());
        // Quotes and backslashes have the highest permitted JSON expansion; bounds apply to encoded bytes too.
        var http=new SecuritySignal.Http(SecuritySignal.Method.CONNECT,SecuritySignal.Route.MATCHED,"/"+"\\\"".repeat(127)+"a",599);
        for(var fact:SecuritySignal.Fact.values()){
            var signal=new SecuritySignal(UUID.randomUUID(),UUID.randomUUID(),Instant.now(),fact,
                    new SecuritySignal.Attribution(SecuritySignal.Scope.TENANT,SecuritySignal.Identity.APP_USER,Long.MAX_VALUE,Long.toString(Long.MAX_VALUE),null,"a".repeat(64),Long.MAX_VALUE,Long.MAX_VALUE),
                    new RequestSource("2001:db8:ffff:ffff:ffff:ffff:ffff:ffff","2001:db9:ffff:ffff:ffff:ffff:ffff:ffff",RequestSource.Basis.TRUSTED_CHAIN,32),new SecuritySignal.Metadata(observer,http));
            byte[] encoded=json.writeValueAsBytes(signal);assertThat(encoded.length).isLessThanOrEqualTo(2048);
            assertThat(json.readValue(encoded,SecuritySignal.class)).isEqualTo(signal);
            var envelope=java.util.Map.of("schema",2,"signal",signal,"ruleVersion",fact.ruleVersion(),"category",fact.category(),"severity",fact.severity(),"outcome",fact.outcome(),"assessment",fact.assessment());
            assertThat(json.writeValueAsBytes(envelope).length).isLessThanOrEqualTo(2048);
        }
    }
    @Test void metadataRejectsUnboundedOrAmbiguousValues(){
        assertThatThrownBy(()->new SecuritySignal.Observer("prod\nsecret","node",UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SecuritySignal.Http(SecuritySignal.Method.GET,SecuritySignal.Route.UNMAPPED,"/secret",404)).isInstanceOf(IllegalArgumentException.class);
        for(String route:java.util.List.of("/x?token=secret","/x#secret","/x\r\nsecret","/"+"x".repeat(256)))
            assertThatThrownBy(()->new SecuritySignal.Http(SecuritySignal.Method.GET,SecuritySignal.Route.MATCHED,route,200)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SecuritySignal.Http(SecuritySignal.Method.GET,SecuritySignal.Route.UNRESOLVED,null,999)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void closedOldFactDecoderMustUpgradeBeforeConsumingNewUnmappedFacts(){
        var old=legacy();assertThat(json.readValue(json.writeValueAsBytes(old),ClosedOldSignal.class).fact()).isEqualTo(ClosedOldFact.AUTHORIZATION_REJECTED);
        var newer=new SecuritySignal(old.eventId(),old.correlationId(),old.occurredAt(),SecuritySignal.Fact.UNMAPPED_REQUEST_OBSERVED,old.attribution(),old.source());
        assertThatThrownBy(()->json.readValue(json.writeValueAsBytes(newer),ClosedOldSignal.class)).isInstanceOf(tools.jackson.databind.exc.InvalidFormatException.class);
    }
}
