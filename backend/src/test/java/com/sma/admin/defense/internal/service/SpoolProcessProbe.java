package com.sma.admin.defense.internal.service;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import com.sma.admin.common.security.SecuritySignal;
import tools.jackson.databind.json.JsonMapper;
/** Separate test JVM exercises OS ownership and abrupt termination, never a production launcher. */
public final class SpoolProcessProbe {
    public static void main(String[] args)throws Exception{
        var spool=new EncryptedSignalSpool(Path.of(args[0]),new byte[32],4,16384,1,2048,JsonMapper.builder().build());
        spool.offer(new SecuritySignal(UUID.fromString(args[1]),UUID.randomUUID(),Instant.now(),SecuritySignal.Fact.TENANT_ENTRY_REJECTED,
                new SecuritySignal.Attribution(SecuritySignal.Scope.PLATFORM,SecuritySignal.Identity.ANONYMOUS,null,null,null,null,null,null),null,
                new SecuritySignal.Metadata(new SecuritySignal.Observer("test","probe-child",UUID.randomUUID()),
                        new SecuritySignal.Http(SecuritySignal.Method.GET,SecuritySignal.Route.UNRESOLVED,null,404))));
        System.out.println("PERSISTED");System.out.flush();
        System.in.read();Runtime.getRuntime().halt(0); // No close/shutdown hook: test actual process-loss recovery.
    }
}
