package com.sma.admin.defense.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.admin.common.security.*;
import tools.jackson.databind.json.JsonMapper;

class EncryptedSignalSpoolTests {
    @TempDir Path temp;
    private final JsonMapper json=JsonMapper.builder().build();
    private final byte[] key=new byte[32];
    record LegacySignal(UUID eventId,UUID correlationId,Instant occurredAt,SecuritySignal.Fact fact,SecuritySignal.Attribution attribution,RequestSource source) {}
    record LegacyEnvelope(int schema,LegacySignal signal,int ruleVersion,SecuritySignal.Category category,
            SecuritySignal.Severity severity,SecuritySignal.Outcome outcome,SecuritySignal.Assessment assessment) {}
    private LegacyEnvelope legacy(SecuritySignal signal){var fact=signal.fact();return new LegacyEnvelope(1,
            new LegacySignal(signal.eventId(),signal.correlationId(),signal.occurredAt(),fact,signal.attribution(),signal.source()),
            fact.ruleVersion(),fact.category(),fact.severity(),fact.outcome(),fact.assessment());}
    private byte[] frame(byte[] plain,UUID id)throws Exception{
        byte[] nonce=new byte[12];new java.security.SecureRandom().nextBytes(nonce);
        var cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,new javax.crypto.spec.SecretKeySpec(key,"AES"),new javax.crypto.spec.GCMParameterSpec(128,nonce));
        cipher.updateAAD(("sma:defense:spool:v1:"+id).getBytes(java.nio.charset.StandardCharsets.US_ASCII));byte[] encrypted=cipher.doFinal(plain);
        return java.nio.ByteBuffer.allocate(16+encrypted.length).putInt(0x53444531).put(nonce).put(encrypted).array();
    }
    private byte[] plaintext(Path file,UUID id)throws Exception{
        var buffer=java.nio.ByteBuffer.wrap(Files.readAllBytes(file));assertThat(buffer.getInt()).isEqualTo(0x53444531);
        byte[] nonce=new byte[12];buffer.get(nonce);byte[] encrypted=new byte[buffer.remaining()];buffer.get(encrypted);
        var cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE,new javax.crypto.spec.SecretKeySpec(key,"AES"),new javax.crypto.spec.GCMParameterSpec(128,nonce));
        cipher.updateAAD(("sma:defense:spool:v1:"+id).getBytes(java.nio.charset.StandardCharsets.US_ASCII));return cipher.doFinal(encrypted);
    }
    @Test void legacyCiphertextWithoutMetadataRecoversAndDuplicateKeepsOriginalBytes()throws Exception{
        var signal=signal(false);Path path=Files.createDirectory(temp.resolve("legacy"));Path file=path.resolve(signal.eventId()+".ready");
        byte[] original=frame(json.writeValueAsBytes(legacy(signal)),signal.eventId());Files.write(file,original);
        try(var spool=open(path)){
            assertThat(spool.read(signal.eventId())).isEqualTo(signal);assertThat(spool.read(signal.eventId()).metadata()).isNull();
            assertThat(spool.offer(signal)).isEqualTo(EncryptedSignalSpool.Offer.DUPLICATE);assertThat(Files.readAllBytes(file)).isEqualTo(original);
            spool.acknowledge(signal.eventId());assertThat(spool.snapshot().records()).isZero();
        }
    }
    @Test void metadataUsesV2AndOldEnvelopeValidationCannotSilentlyAcceptIt()throws Exception{
        var base=signal(false);var signal=new SecuritySignal(base.eventId(),base.correlationId(),base.occurredAt(),base.fact(),base.attribution(),base.source(),
                new SecuritySignal.Metadata(new SecuritySignal.Observer("staging","node-a",UUID.randomUUID()),new SecuritySignal.Http(SecuritySignal.Method.GET,SecuritySignal.Route.MATCHED,"/api/defense/events/{eventId}",403)));
        Path path=temp.resolve("metadata");try(var spool=open(path)){assertThat(spool.offer(signal)).isEqualTo(EncryptedSignalSpool.Offer.STORED);}
        Path file=path.resolve(signal.eventId()+".ready");byte[] plain=plaintext(file,signal.eventId());assertThat(json.readTree(plain).path("schema").asInt()).isEqualTo(2);
        var oldReader=JsonMapper.builder().disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        // Original consumer verifies exact Envelope.of(signal), whose schema is 1; even a lenient JSON reader rejects v2.
        assertThat(oldReader.readValue(plain,LegacyEnvelope.class)).isNotEqualTo(legacy(signal));assertThat(file).exists();
        try(var spool=open(path)){assertThat(spool.read(signal.eventId())).isEqualTo(signal);assertThat(spool.offer(signal)).isEqualTo(EncryptedSignalSpool.Offer.DUPLICATE);spool.acknowledge(signal.eventId());}
    }
    private EncryptedSignalSpool open(Path path)throws IOException{return new EncryptedSignalSpool(path,key,4,16384,1,2048,json);}
    private SecuritySignal signal(boolean priority){return new SecuritySignal(UUID.randomUUID(),UUID.randomUUID(),Instant.now(),
            priority?SecuritySignal.Fact.SIGNATURE_REPLAY_REJECTED:SecuritySignal.Fact.AUTHORIZATION_REJECTED,
            new SecuritySignal.Attribution(SecuritySignal.Scope.TENANT,SecuritySignal.Identity.ADMIN_USER,9123L,"77","admin",null,null,null),
            new RequestSource("198.51.100.17","203.0.113.8",RequestSource.Basis.TRUSTED_CHAIN,1));}
    @Test void encryptedRoundtripKeepsOriginalEnvelopeAndAckSurvivesReopen()throws Exception{
        var path=temp.resolve("store");var signal=signal(false);byte[] supplied=key.clone();
        try(var spool=new EncryptedSignalSpool(path,supplied,4,16384,1,2048,json)){
            Arrays.fill(supplied,(byte)9);assertThat(spool.offer(signal)).isEqualTo(EncryptedSignalSpool.Offer.STORED);
            assertThat(spool.offer(signal)).isEqualTo(EncryptedSignalSpool.Offer.DUPLICATE);
            assertThat(spool.snapshot().records()).isEqualTo(1);assertThat(spool.read(signal.eventId())).isEqualTo(signal);
            var changed=new SecuritySignal(signal.eventId(),signal.correlationId(),signal.occurredAt(),SecuritySignal.Fact.REQUEST_INPUT_REJECTED,signal.attribution(),signal.source());
            assertThat(spool.offer(changed)).isEqualTo(EncryptedSignalSpool.Offer.CONFLICT);
            assertThat(new String(Files.readAllBytes(path.resolve(signal.eventId()+".ready")),java.nio.charset.StandardCharsets.ISO_8859_1))
                    .doesNotContain("198.51.100.17","203.0.113.8","ADMIN_USER","AUTHORIZATION_REJECTED");
        }
        try(var spool=open(path)){assertThat(spool.read(signal.eventId())).isEqualTo(signal);spool.acknowledge(signal.eventId());assertThat(spool.snapshot().bytes()).isZero();}
        try(var spool=open(path)){assertThat(spool.items()).isEmpty();}
    }
    @Test void countReservationAndByteBudgetSurviveRestart()throws Exception{
        Path path=temp.resolve("count");var high=signal(true);
        try(var spool=open(path)){
            for(int i=0;i<3;i++)assertThat(spool.offer(signal(false))).isEqualTo(EncryptedSignalSpool.Offer.STORED);
            assertThat(spool.offer(signal(false))).isEqualTo(EncryptedSignalSpool.Offer.CAPACITY);
            assertThat(spool.offer(high)).isEqualTo(EncryptedSignalSpool.Offer.STORED);
            assertThat(spool.offer(signal(true))).isEqualTo(EncryptedSignalSpool.Offer.CAPACITY);
            assertThat(spool.items().getFirst().eventId()).isEqualTo(high.eventId());
        }
        try(var spool=open(path)){assertThat(spool.snapshot().records()).isEqualTo(4);assertThat(spool.offer(signal(true))).isEqualTo(EncryptedSignalSpool.Offer.CAPACITY);}
        Path bytes=temp.resolve("bytes");
        try(var spool=new EncryptedSignalSpool(bytes,key,20,4096,1,2048,json)){
            int accepted=0;while(spool.offer(signal(false))==EncryptedSignalSpool.Offer.STORED)accepted++;
            assertThat(accepted).isBetween(1,10);assertThat(spool.snapshot().bytes()).isLessThanOrEqualTo(2048);
            assertThat(spool.offer(signal(true))).isEqualTo(EncryptedSignalSpool.Offer.STORED);assertThat(spool.snapshot().bytes()).isLessThanOrEqualTo(4096);
        }
    }
    @Test void completePendingFileIsRecoveredButTruncatedFileIsPreservedAndRejected()throws Exception{
        Path path=temp.resolve("pending");var signal=signal(false);
        try(var spool=open(path)){spool.offer(signal);}
        Files.move(path.resolve(signal.eventId()+".ready"),path.resolve(signal.eventId()+".pending"));
        try(var spool=open(path)){assertThat(spool.read(signal.eventId())).isEqualTo(signal);assertThat(path.resolve(signal.eventId()+".pending")).doesNotExist();}
        Path partial=path.resolve(UUID.randomUUID()+".pending");Files.write(partial,new byte[]{1,2,3});
        assertThatThrownBy(()->open(path)).isInstanceOf(IOException.class);assertThat(Files.readAllBytes(partial)).containsExactly((byte)1,(byte)2,(byte)3);
        assertThat(path.resolve(signal.eventId()+".ready")).exists();
    }
    @Test void tamperingWrongKeyOrRenamingCannotYieldForgedEventsOrDeleteEvidence()throws Exception{
        Path path=temp.resolve("tamper");var signal=signal(true);
        try(var spool=open(path)){spool.offer(signal);}
        byte[] wrong=key.clone();wrong[0]=1;
        assertThatThrownBy(()->new EncryptedSignalSpool(path,wrong,4,16384,1,2048,json)).isInstanceOf(IOException.class);
        Path original=path.resolve(signal.eventId()+".ready"),renamed=path.resolve(UUID.randomUUID()+".ready");Files.move(original,renamed);
        assertThatThrownBy(()->open(path)).isInstanceOf(IOException.class);Files.move(renamed,original);
        try(var spool=open(path)){
            byte[] frame=Files.readAllBytes(original);frame[frame.length-1]^=1;Files.write(original,frame);
            assertThatThrownBy(()->spool.read(signal.eventId())).isInstanceOf(IOException.class);
            assertThatThrownBy(()->spool.acknowledge(signal.eventId())).isInstanceOf(IOException.class);assertThat(original).exists();
        }
        assertThatThrownBy(()->open(path)).isInstanceOf(IOException.class);assertThat(original).exists();
    }
    @Test void unknownArtifactsOverBudgetAndCollidingAttemptsFailWithoutOverwriting()throws Exception{
        Path path=temp.resolve("unknown");Files.createDirectories(path);Path marker=Files.writeString(path.resolve("unrelated.txt"),"keep");
        assertThatThrownBy(()->open(path)).isInstanceOf(IOException.class);assertThat(Files.readString(marker)).isEqualTo("keep");
        Path crowded=temp.resolve("crowded");try(var spool=open(crowded)){spool.offer(signal(false));spool.offer(signal(false));}
        assertThatThrownBy(()->new EncryptedSignalSpool(crowded,key,1,16384,0,0,json)).isInstanceOf(IOException.class);
        Path collision=temp.resolve("collision");var signal=signal(false);
        try(var spool=open(collision)){
            Path pending=Files.writeString(collision.resolve(signal.eventId()+".pending"),"do-not-overwrite");
            assertThatThrownBy(()->spool.offer(signal)).isInstanceOf(IOException.class);assertThat(Files.readString(pending)).isEqualTo("do-not-overwrite");
            assertThatThrownBy(()->spool.offer(signal(false))).isInstanceOf(IOException.class);
        }
    }
    @Test void lockAndClosedInstanceNeverPermitConcurrentDirectoryUse()throws Exception{
        Path path=temp.resolve("lock");var first=open(path);
        try{assertThatThrownBy(()->open(path)).isInstanceOf(IOException.class);first.offer(signal(false));}
        finally{first.close();}
        first.close();assertThatThrownBy(first::items).isInstanceOf(IOException.class);
        try(var next=open(path)){assertThat(next.snapshot().records()).isEqualTo(1);}
    }
    @Test void redirectedDirectoryAndArtifactsAreRejected()throws Exception{
        Path outside=Files.createDirectory(temp.resolve("outside")),link=temp.resolve("link");
        boolean junction=false;
        try{Files.createSymbolicLink(link,outside);}catch(IOException|UnsupportedOperationException unavailable){junction(link,outside);junction=true;}
        assertThatThrownBy(()->open(link.resolve("spool"))).isInstanceOf(IOException.class);
        Path path=temp.resolve("files");try(var spool=open(path)){assertThat(spool.items()).isEmpty();}
        Path target=Files.writeString(outside.resolve("file"),"untouched"),artifact=path.resolve(UUID.randomUUID()+".ready");
        if(junction)junction(artifact,outside);else Files.createSymbolicLink(artifact,target);
        assertThatThrownBy(()->open(path)).isInstanceOf(IOException.class);assertThat(Files.readString(target)).isEqualTo("untouched");
    }
    private void junction(Path link,Path target)throws Exception{
        // Windows junctions need no symbolic-link privilege and exercise the actual reparse-point boundary.
        assertThat(System.getProperty("os.name")).startsWith("Windows");
        String command="$null = New-Item -ItemType Junction -Path '"+link.toString().replace("'","''")+"' -Target '"+target.toString().replace("'","''")+"' -ErrorAction Stop";
        var process=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-EncodedCommand",
                Base64.getEncoder().encodeToString(command.getBytes(java.nio.charset.StandardCharsets.UTF_16LE)))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try{assertThat(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();}
        finally{if(process.isAlive()){process.destroyForcibly();process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS);}}
    }
    @Test void actualProcessExitRetainsForcedRecordAndOtherProcessCannotTakeLock()throws Exception{
        Path path=temp.resolve("process");var id=UUID.randomUUID();String executable=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        if(!Files.exists(Path.of(executable)))executable=Path.of(System.getProperty("java.home"),"bin","java").toString();
        var command=List.of(executable,"-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),SpoolProcessProbe.class.getName(),path.toString(),id.toString());
        Process child=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try{
            try(var reader=child.inputReader()){
                var ready=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return reader.readLine();}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}});
                assertThat(ready.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("PERSISTED");
                assertThatThrownBy(()->open(path)).isInstanceOf(IOException.class);
                child.outputWriter().write("exit\n");child.outputWriter().flush();
                assertThat(child.waitFor(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(child.exitValue()).isEqualTo(0);
            }
        }finally{if(child.isAlive()){child.destroyForcibly();assertThat(child.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}}
        try(var spool=open(path)){
            var recovered=spool.read(id);assertThat(recovered.eventId()).isEqualTo(id);
            assertThat(recovered.metadata().observer().nodeId()).isEqualTo("probe-child");
            assertThat(recovered.metadata().http()).isEqualTo(new SecuritySignal.Http(SecuritySignal.Method.GET,SecuritySignal.Route.UNRESOLVED,null,404));
            spool.acknowledge(id);
        }
        try(var spool=open(path)){assertThat(spool.snapshot().records()).isZero();}
    }
    @Test void authenticatedButUnsupportedFormatOrClassificationIsPreservedForReview()throws Exception{
        var signal=signal(false);Path path=temp.resolve("version");
        try(var spool=open(path)){spool.offer(signal);}
        Path file=path.resolve(signal.eventId()+".ready");byte[] frame=Files.readAllBytes(file);
        var raw=java.nio.ByteBuffer.wrap(frame);int magic=raw.getInt();byte[] nonce=new byte[12];raw.get(nonce);byte[] cipherText=new byte[raw.remaining()];raw.get(cipherText);
        var cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        byte[] aad=("sma:defense:spool:v1:"+signal.eventId()).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE,new javax.crypto.spec.SecretKeySpec(key,"AES"),new javax.crypto.spec.GCMParameterSpec(128,nonce));cipher.updateAAD(aad);
        String content=new String(cipher.doFinal(cipherText),java.nio.charset.StandardCharsets.UTF_8);assertThat(content).contains("\"ruleVersion\":1");
        String changed=content.replace("\"ruleVersion\":1","\"ruleVersion\":2");byte[] nextNonce=new byte[12];new java.security.SecureRandom().nextBytes(nextNonce);
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,new javax.crypto.spec.SecretKeySpec(key,"AES"),new javax.crypto.spec.GCMParameterSpec(128,nextNonce));cipher.updateAAD(aad);
        byte[] encrypted=cipher.doFinal(changed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(file,java.nio.ByteBuffer.allocate(16+encrypted.length).putInt(magic).put(nextNonce).put(encrypted).array());
        assertThatThrownBy(()->open(path)).isInstanceOf(IOException.class);assertThat(file).exists();
    }
}
