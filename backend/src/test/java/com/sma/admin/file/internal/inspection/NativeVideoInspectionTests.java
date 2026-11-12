package com.sma.admin.file.internal.inspection;

import static org.assertj.core.api.Assertions.*;
import static com.sma.admin.file.internal.inspection.InspectionProtocol.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;
import com.sma.admin.file.FileFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Requires an explicitly selected native tool. Passing this suite is not container/network isolation evidence. */
class NativeVideoInspectionTests {
    @TempDir Path directory;private String previous,probe;
    @BeforeEach void configure(){probe=System.getProperty("sma.file.test.ffprobe");Assumptions.assumeTrue(probe!=null,"Real ffprobe path not configured; native acceptance not run");
        assertThat(Path.of(probe)).isAbsolute().isRegularFile();previous=System.getProperty("sma.file.worker.ffprobe");System.setProperty("sma.file.worker.ffprobe",probe);}
    @AfterEach void restore(){if(probe==null)return;if(previous==null)System.clearProperty("sma.file.worker.ffprobe");else System.setProperty("sma.file.worker.ffprobe",previous);}
    private String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @Test void realProbeAcceptsBothSyntheticClipsAndWorkerReportBindsTheirFullSource()throws Exception{
        for(String name:List.of("synthetic-video.mp4","synthetic-audio-video.mp4")){
            byte[] bytes=VideoContentInspectionTests.fixture(name);Path source=directory.resolve(name);Files.write(source,bytes);
            var request=new Request(1,FileFormat.MP4,bytes.length,hash(bytes),0);var report=ContentInspectionWorker.inspect(source,directory,request);
            assertThat(report.verdict()).as(name+":"+report.code()).isEqualTo(Verdict.PASS);assertThat(verify(request,mapper().writeValueAsBytes(report))).isEqualTo(report);
        }
    }
    @Test void nestedJvmAndNativeChildUseSameRestrictedProbeAndArchiveMemberChecks()throws Exception{
        byte[] video=VideoContentInspectionTests.fixture("synthetic-video.mp4");var output=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(output)){zip.putNextEntry(new ZipEntry("clip.mp4"));zip.write(video);zip.closeEntry();}
        byte[] bytes=output.toByteArray();Path source=directory.resolve("movie.zip"),scratch=Files.createDirectory(directory.resolve("scratch"));Files.write(source,bytes);var request=new Request(1,FileFormat.ZIP,bytes.length,hash(bytes),1024*1024);
        var command=List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"-Xmx256m","-XX:ActiveProcessorCount=1",
                "-Dsma.file.worker.ffprobe="+probe,"-cp",System.getProperty("java.class.path"),ContentInspectionWorker.class.getName(),source.toString(),scratch.toString());
        try(var runner=new BoundedInspectionProcess(1)){
            var result=runner.execute(command,directory,mapper().writeValueAsBytes(request),Duration.ofSeconds(30));assertThat(result.exitCode()).isZero();assertThat(verify(request,result.output()).verdict()).isEqualTo(Verdict.PASS);
        }
        try(var files=Files.list(scratch)){assertThat(files.toList()).isEmpty();}
    }
}
