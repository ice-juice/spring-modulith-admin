package com.sma.worker;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import com.sma.extension.protocol.*;

/** Run under an independent off-host account. Append-only local copies anchor the source chain; OS/WORM retention is operator-owned. */
public final class AuditMirror {
    private record Reply(int code,String error,String message,AuditStream.Batch data) {}
    public record Head(long sequence,String hash) {}
    public static Head verify(Path root)throws IOException {
        Files.createDirectories(root);Checks.require(!Files.isSymbolicLink(root) && root.toAbsolutePath().normalize().equals(root.toRealPath()),"unsafe-audit-mirror-root");
        Head head=new Head(0,"0".repeat(64));
        try(var paths=Files.list(root)) {
            var files=paths.filter(p -> p.getFileName().toString().matches("[0-9]{20}\\.json")).limit(1_000_001).sorted().toList();Checks.require(files.size()<=1_000_000,"audit-mirror-segment-limit");
            for(Path file:files) {
                Checks.require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) && Files.size(file)<=16384,"invalid-audit-mirror-entry");
                var entry=ProtocolJson.read(Files.readAllBytes(file),AuditStream.Hashed.class);String hash=AuditStream.verify(head.sequence(),head.hash(),entry);
                Checks.require(file.getFileName().toString().equals(String.format(java.util.Locale.ROOT,"%020d.json",entry.entry().sequence())),"audit-mirror-filename-mismatch");head=new Head(entry.entry().sequence(),hash);
            }
        }return head;
    }
    public static Head append(Path root,Head head,AuditStream.Batch batch)throws IOException {
        Checks.require(batch.anchorSequence()==head.sequence() && batch.anchorHash().equals(head.hash()) && batch.headSequence()>=head.sequence(),"audit-anchor-mismatch");
        Head next=head;
        // Verify the entire incoming batch before persisting any part of a malformed response.
        for(var entry:batch.entries()){String hash=AuditStream.verify(next.sequence(),next.hash(),entry);next=new Head(entry.entry().sequence(),hash);}
        Checks.require(next.sequence()<=batch.headSequence() && (!batch.entries().isEmpty() || batch.headSequence()==head.sequence()),"audit-stream-incomplete");
        for(var entry:batch.entries()) {
            Path file=root.resolve(String.format(java.util.Locale.ROOT,"%020d.json",entry.entry().sequence()));byte[] bytes=ProtocolJson.write(entry);
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)) {Checks.require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && Files.size(file)<=16384 && java.util.Arrays.equals(bytes,Files.readAllBytes(file)),"audit-mirror-rewrite-rejected");continue;}
            Path staging=Files.createTempFile(root,"audit-",".partial");
            try {Files.write(staging,bytes);try(var channel=java.nio.channels.FileChannel.open(staging,StandardOpenOption.WRITE)){channel.force(true);}Files.move(staging,file);}
            finally{Files.deleteIfExists(staging);}
        }return next;
    }
    public static void synchronize(URI source,String token,Path directory)throws Exception {
        Checks.require("https".equals(source.getScheme()) && source.getHost()!=null && source.getUserInfo()==null && source.getQuery()==null && source.getFragment()==null,"audit-https-required");
        Checks.require(token.matches("[A-Za-z0-9_-]{32,128}"),"invalid-auditor-token");Path root=directory.toAbsolutePath().normalize();Files.createDirectories(root);
        try(var lockFile=java.nio.channels.FileChannel.open(root.resolve("mirror.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var lock=lockFile.tryLock()) {
            Checks.require(lock!=null,"audit-mirror-busy");var head=verify(root);
            var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
            for(int page=0;page<100;page++) {
                var request=HttpRequest.newBuilder(source.resolve("/release/v1/machines/auditors/audit?after="+head.sequence()+"&expectedHash="+head.hash()+"&limit=100")).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+token).GET().build();
                var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());AuditStream.Batch batch;
                try(var input=response.body()){Checks.require(response.statusCode()==200,"audit-export-rejected");var reply=ProtocolJson.read(input.readNBytes(ProtocolJson.MAX_DOCUMENT_BYTES+1),Reply.class);Checks.require(reply.code()==0,"audit-export-rejected");batch=reply.data();}
                head=append(root,head,batch);
                var acknowledgement=HttpRequest.newBuilder(source.resolve("/release/v1/machines/auditors/ack")).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+token).header("Content-Type","application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(ProtocolJson.write(head))).build();
                var ack=http.send(acknowledgement,HttpResponse.BodyHandlers.discarding());Checks.require(ack.statusCode()==200,"audit-ack-rejected");
                if(head.sequence()==batch.headSequence())return;
            }
        }
    }
}
