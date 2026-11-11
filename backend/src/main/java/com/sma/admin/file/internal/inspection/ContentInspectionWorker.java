package com.sma.admin.file.internal.inspection;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Set;
import com.sma.admin.file.FileFormat;
import com.sma.admin.file.internal.service.FileContentProbe;
import static com.sma.admin.file.internal.inspection.InspectionProtocol.*;

/** Standalone entry point for a restricted worker image, never a Spring bean or an in-process upload fallback. */
public final class ContentInspectionWorker {
    private ContentInspectionWorker(){}
    public static void main(String[] args){
        // Parent/container enforces CPU, memory, filesystem, network and hard process lifetime independently.
        Thread watchdog=Thread.ofPlatform().daemon().name("inspection-deadline").start(()->{
            try{Thread.sleep(90000);Runtime.getRuntime().halt(124);}catch(InterruptedException stopped){Thread.currentThread().interrupt();}
        });
        try{
            if(args.length!=2)throw new IllegalArgumentException("Worker paths required");
            byte[] bytes=System.in.readNBytes(MESSAGE_BYTES+1);if(bytes.length>MESSAGE_BYTES)throw new IllegalArgumentException("Request too large");
            Request request=mapper().readValue(bytes,Request.class);
            Report report=inspect(Path.of(args[0]),Path.of(args[1]),request);
            byte[] result=mapper().writeValueAsBytes(report);if(result.length>MESSAGE_BYTES)throw new IllegalStateException("Report too large");
            System.out.write(result);System.out.flush();
        }catch(Exception invalid){System.exit(2);} // No parser diagnostics or customer content in stdout/stderr.
        finally{watchdog.interrupt();}
    }
    static Report inspect(Path source,Path output,Request request){
        try{
            var attributes=Files.readAttributes(source,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther() || attributes.size()!=request.size())return report(request,Verdict.REJECT,Code.CONTENT_REJECTED);
            if(!Files.isDirectory(output,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(output))return report(request,Verdict.ERROR,Code.IO);
            if(!Set.of(FileFormat.TEXT,FileFormat.JSON,FileFormat.CSV).contains(request.format()))return report(request,Verdict.ERROR,Code.PROCESSOR_UNAVAILABLE);
            if(request.size()>TextContentInspection.MAXIMUM_BYTES)return report(request,Verdict.ERROR,Code.CAPACITY);
            FileContentProbe.inspect(Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS),request.format(),request.size(),request.checksum(),
                    new FileContentProbe.Limits(TextContentInspection.MAXIMUM_BYTES,Duration.ofSeconds(60)));
            try{
                if(request.format()==FileFormat.JSON)TextContentInspection.json(source);
                if(request.format()==FileFormat.CSV)TextContentInspection.csv(source);
            }catch(IOException | RuntimeException rejected){return report(request,Verdict.REJECT,Code.CONTENT_REJECTED);}
            // Host must separately verify immutable source/derived digests and complete malware scanning before publication.
            return report(request,Verdict.PASS,Code.NONE);
        }catch(FileContentProbe.Rejected rejected){return report(request,rejected.reason()==FileContentProbe.Reason.DEADLINE?Verdict.ERROR:Verdict.REJECT,
                rejected.reason()==FileContentProbe.Reason.DEADLINE?Code.DEADLINE:Code.CONTENT_REJECTED);}
         catch(IOException unavailable){return report(request,Verdict.ERROR,Code.IO);}
    }
}
