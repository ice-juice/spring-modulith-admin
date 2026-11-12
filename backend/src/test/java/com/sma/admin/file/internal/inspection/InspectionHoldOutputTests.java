package com.sma.admin.file.internal.inspection;

import static org.assertj.core.api.Assertions.*;
import static com.sma.admin.file.internal.inspection.InspectionProtocol.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import com.sma.admin.file.FileFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real child process and pipes. No container daemon or OS isolation is simulated by this test. */
class InspectionHoldOutputTests {
    @TempDir Path directory;
    private record Fixture(Path source,Path output,Request request){}
    private Fixture fixture()throws Exception{
        Path source=directory.resolve(UUID.randomUUID()+".blob"),output=Files.createDirectory(directory.resolve(UUID.randomUUID().toString()));
        ImageIO.write(new BufferedImage(8,6,BufferedImage.TYPE_INT_ARGB),"png",source.toFile());byte[] bytes=Files.readAllBytes(source);
        String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));return new Fixture(source,output,new Request(1,FileFormat.PNG,bytes.length,checksum,1024*1024));
    }
    private Process start(Fixture fixture,String mode)throws Exception{
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),
                "-Xmx128m","-XX:ActiveProcessorCount=1","-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),ContentInspectionWorker.class.getName(),fixture.source.toString(),fixture.output.toString()));
        if(mode!=null)command.add(mode);var builder=new ProcessBuilder(command).directory(directory.toFile());builder.environment().clear();
        for(String key:List.of("SystemRoot","WINDIR")){String value=System.getenv(key);if(value!=null)builder.environment().put(key,value);}
        builder.environment().put("TMP",directory.toString());builder.environment().put("TEMP",directory.toString());return builder.start();
    }
    private void stop(Process process)throws Exception{if(process.isAlive()){process.destroyForcibly();assertThat(process.waitFor(5,TimeUnit.SECONDS)).isTrue();}}
    private byte[] report(Process process)throws Exception{
        try(var executor=Executors.newSingleThreadExecutor()){
            var read=executor.submit(()->InspectionFrames.read(process.getInputStream()));
            try{return read.get(10,TimeUnit.SECONDS);}catch(Exception failure){stop(process);throw failure;}
        }
    }
    @Test void keepsOutputAliveUntilHostChecksDigestThenAcknowledges()throws Exception{
        var fixture=fixture();Process child=start(fixture,"--hold-output");
        try{
            InspectionFrames.write(child.getOutputStream(),mapper().writeValueAsBytes(fixture.request));
            Report report=verify(fixture.request,report(child));assertThat(report.verdict()).isEqualTo(Verdict.PASS);assertThat(child.isAlive()).isTrue();
            byte[] derivative=Files.readAllBytes(fixture.output.resolve(report.normalized().name()));assertThat(derivative.length).isEqualTo(report.normalized().size());
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(derivative))).isEqualTo(report.normalized().checksum());
            assertThat(child.waitFor(150,TimeUnit.MILLISECONDS)).isFalse();
            child.getOutputStream().write(InspectionFrames.ACK);child.getOutputStream().flush();assertThat(child.waitFor(5,TimeUnit.SECONDS)).isTrue();assertThat(child.exitValue()).isZero();
            assertThat(child.getInputStream().readAllBytes()).isEmpty();assertThat(child.getErrorStream().readAllBytes()).isEmpty();
        }finally{stop(child);}
    }
    @Test void eofOrWrongAckAfterAReportDoesNotClaimSuccessfulTransfer()throws Exception{
        for(boolean eof:List.of(true,false)){
            var fixture=fixture();Process child=start(fixture,"--hold-output");try{
                InspectionFrames.write(child.getOutputStream(),mapper().writeValueAsBytes(fixture.request));assertThat(verify(fixture.request,report(child)).verdict()).isEqualTo(Verdict.PASS);
                if(eof)child.getOutputStream().close();else{child.getOutputStream().write(0);child.getOutputStream().flush();}
                assertThat(child.waitFor(5,TimeUnit.SECONDS)).isTrue();assertThat(child.exitValue()).isEqualTo(2);
            }finally{stop(child);}
        }
    }
    @Test void badModesOversizedOrIncompleteFramesCannotProduceReports()throws Exception{
        for(String scenario:List.of("mode","size","partial")){
            var fixture=fixture();Process child=start(fixture,scenario.equals("mode")?"--unknown":"--hold-output");try{
                try(var input=child.getOutputStream()){
                    input.write(scenario.equals("size")?("x".repeat(16385)+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII):new byte[]{'{'});
                }catch(IOException closed){/* Child may reject its arguments before reading. */}
                assertThat(child.waitFor(5,TimeUnit.SECONDS)).isTrue();assertThat(child.exitValue()).isEqualTo(2);assertThat(child.getInputStream().readAllBytes()).isEmpty();
            }finally{stop(child);}
        }
    }
}
