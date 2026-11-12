package com.sma.admin.file.internal.inspection;

import static org.assertj.core.api.Assertions.*;
import static com.sma.admin.file.internal.inspection.InspectionProtocol.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import com.sma.admin.file.FileFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentInspectionWorkerTests {
    @TempDir Path directory;
    private String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private Request request(FileFormat format,byte[] bytes)throws Exception{return new Request(1,format,bytes.length,hash(bytes),0);}
    private Path source(byte[] bytes)throws Exception{Path path=directory.resolve(UUID.randomUUID()+".blob");Files.write(path,bytes);return path;}
    private byte[] utf8(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private Report inspect(FileFormat format,String text)throws Exception{byte[] bytes=utf8(text);return ContentInspectionWorker.inspect(source(bytes),directory,request(format,bytes));}
    private List<String> java(){return new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),
            "-Xmx64m","-XX:MaxDirectMemorySize=16m","-XX:ActiveProcessorCount=1","-Djava.awt.headless=true","-cp",System.getProperty("java.class.path")));}
    @Test void realStandaloneProcessUsesBoundedProtocolAndDoesNotBootTheHost()throws Exception{
        byte[] bytes=utf8("{\"message\":\"你好\",\"items\":[1,true,null]}");var request=request(FileFormat.JSON,bytes);var command=java();
        command.addAll(List.of(ContentInspectionWorker.class.getName(),source(bytes).toString(),directory.toString()));
        try(var process=new BoundedInspectionProcess(1)){
            var result=process.execute(command,directory,mapper().writeValueAsBytes(request),Duration.ofSeconds(15));
            assertThat(result.exitCode()).isZero();var report=verify(request,result.output());assertThat(report.verdict()).isEqualTo(Verdict.PASS);assertThat(report.normalized()).isNull();
            assertThat(new String(result.output(),StandardCharsets.UTF_8)).doesNotContain("Spring Boot","你好","Exception");
        }
    }
    @Test void strictJsonRejectsDuplicateTrailingInvalidDeepAndOversizedStringContent()throws Exception{
        for(String value:List.of("not JSON", "{\"a\":1,\"a\":2}","{} []","{\"broken\":}","[".repeat(65)+"0"+"]".repeat(65),"\""+"a".repeat(262145)+"\""))
            assertThat(inspect(FileFormat.JSON,value).verdict()).as(value.substring(0,Math.min(value.length(),30))).isEqualTo(Verdict.REJECT);
        for(String value:List.of("null","123","\"hello\"","[]","{}","{\"a\":[true,2]}"))assertThat(inspect(FileFormat.JSON,value).verdict()).isEqualTo(Verdict.PASS);
    }
    @Test void csvRejectsFormulaInjectionBrokenQuotesAndResourceAbuseButAcceptsNumericNegatives()throws Exception{
        for(String value:List.of("name,value\nfoo,=HYPERLINK(1)\n","name,value\nfoo,\" \t@SUM(1,2)\"","foo,+cmd|x","foo,-1+2", "a,\"unterminated","a,ab\"c","a,\"ok\"tail",
                "x,\u00a0=SUM(1)","x,\u200b@SUM(1)","a".repeat(65537),",".repeat(4096)))assertThat(inspect(FileFormat.CSV,value).verdict()).isEqualTo(Verdict.REJECT);
        for(String value:List.of("\ufeffname,value\r\n会员,-12.5\r\n","name,\"quoted,cell\"\n","a,\"escaped \"\"quote\"\"\"\n","x,+12.5e-2","a,\"two\nlines\""))
            assertThat(inspect(FileFormat.CSV,value).verdict()).isEqualTo(Verdict.PASS);
    }
    @Test void malformedVideoCapacityDigestAndMissingSourcesCannotPass()throws Exception{
        byte[] bytes=utf8("content");var input=source(bytes);
        assertThat(ContentInspectionWorker.inspect(input,directory,request(FileFormat.MP4,bytes)).verdict()).isEqualTo(Verdict.REJECT);
        assertThat(ContentInspectionWorker.inspect(input,directory,new Request(1,FileFormat.TEXT,bytes.length,"a".repeat(64),0)).verdict()).isEqualTo(Verdict.REJECT);
        assertThat(ContentInspectionWorker.inspect(directory.resolve("missing"),directory,request(FileFormat.TEXT,bytes)).verdict()).isEqualTo(Verdict.ERROR);
        Path large=directory.resolve("large.blob");try(var file=new java.io.RandomAccessFile(large.toFile(),"rw")){file.setLength(TextContentInspection.MAXIMUM_BYTES+1);}
        assertThat(ContentInspectionWorker.inspect(large,directory,new Request(1,FileFormat.TEXT,TextContentInspection.MAXIMUM_BYTES+1,"a".repeat(64),0)).code()).isEqualTo(Code.CAPACITY);
    }
    @Test void reportsBindExactSourceFormatAndBudgetAndCannotInventPathsOrSkipImageNormalization()throws Exception{
        byte[] bytes=utf8("text");var request=request(FileFormat.TEXT,bytes);var valid=mapper().writeValueAsBytes(report(request,Verdict.PASS,Code.NONE));assertThat(verify(request,valid).verdict()).isEqualTo(Verdict.PASS);
        var another=new Request(1,FileFormat.TEXT,bytes.length,"a".repeat(64),0);assertThatThrownBy(()->verify(another,valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->verify(request,utf8(new String(valid,StandardCharsets.UTF_8)+"{}"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->new Normalized("../outside.png",FileFormat.PNG,1,"a".repeat(64))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new Normalized("normalized.png",FileFormat.TEXT,1,"a".repeat(64))).isInstanceOf(IllegalArgumentException.class);
        var image=new Request(1,FileFormat.PNG,bytes.length,hash(bytes),100);
        assertThatThrownBy(()->verify(image,mapper().writeValueAsBytes(report(image,Verdict.PASS,Code.NONE)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void workerRejectsMalformedOrDuplicateControlMessagesWithoutEchoingPayload()throws Exception{
        var command=java();command.addAll(List.of(ContentInspectionWorker.class.getName(),source(utf8("x")).toString(),directory.toString()));
        try(var process=new BoundedInspectionProcess(1)){
            var result=process.execute(command,directory,utf8("{\"schema\":1,\"schema\":2,\"secret\":\"never echo\"}"),Duration.ofSeconds(15));
            assertThat(result.exitCode()).isNotZero();assertThat(result.output()).isEmpty();
        }
    }
}
