package com.sma.admin.file.internal.inspection;

import static org.assertj.core.api.Assertions.*;
import static com.sma.admin.file.internal.inspection.InspectionProtocol.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import com.sma.admin.file.FileFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveContentInspectionTests {
    @TempDir Path directory;
    private byte[] text(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private byte[] zip(Map<String,byte[]> entries)throws Exception{
        var bytes=new ByteArrayOutputStream();try(var out=new ZipOutputStream(bytes)){
            for(var entry:entries.entrySet()){var item=new ZipEntry(entry.getKey());out.putNextEntry(item);out.write(entry.getValue());out.closeEntry();}
        }return bytes.toByteArray();
    }
    private byte[] png()throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(8,6,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
    private record Run(Report report,Request request,Path source,Path scratch){}
    private Run run(FileFormat format,byte[] bytes,long budget)throws Exception{
        Path source=directory.resolve(UUID.randomUUID()+".blob"),scratch=Files.createDirectory(directory.resolve(UUID.randomUUID().toString()));Files.write(source,bytes);
        var request=new Request(1,format,bytes.length,hash(bytes),budget);var report=ContentInspectionWorker.inspect(source,scratch,request);
        try(var files=Files.list(scratch)){assertThat(files.toList()).isEmpty();}return new Run(report,request,source,scratch);
    }
    private Run run(FileFormat format,byte[] bytes)throws Exception{return run(format,bytes,16L*1024*1024);}
    @Test void archiveChecksTextJsonCsvImagesAndNestedPackagesWithoutPublishingMemberFiles()throws Exception{
        byte[] nested=zip(Map.of("notes.txt",text("nested")));var entries=Map.of("plain.txt",text("你好"),"empty.txt",new byte[0],"data.json",text("{\"ok\":true}"),
                "data.csv",text("name,value\nexample,12"),"image.png",png(),"nested.zip",nested);
        var result=run(FileFormat.ZIP,zip(entries));assertThat(result.report.verdict()).isEqualTo(Verdict.PASS);assertThat(result.report.normalized()).isNull();
        assertThat(verify(result.request,mapper().writeValueAsBytes(result.report))).isEqualTo(result.report);
    }
    @Test void oneInvalidOrDisguisedMemberRejectsTheWholeArchive()throws Exception{
        for(var malicious:Map.of("bad.json",text("{bad}"),"bad.csv",text("=WEBSERVICE(1)"),"fake.png",text("not a png"),"shell.php.txt",text("text"),"script.txt",text("<?php echo 1;"),"tool.exe",text("MZ"),"empty.json",new byte[0]).entrySet()){
            var entries=new LinkedHashMap<String,byte[]>();entries.put("good.txt",text("good"));entries.put(malicious.getKey(),malicious.getValue());
            assertThat(run(FileFormat.ZIP,zip(entries)).report.verdict()).as(malicious.getKey()).isEqualTo(Verdict.REJECT);
        }
    }
    private byte[] docx(byte[] media)throws Exception{
        String types="<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'><Default Extension='xml' ContentType='application/xml'/>"
                +"<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/><Default Extension='png' ContentType='image/png'/>"
                +"<Override PartName='/word/document.xml' ContentType='application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml'/></Types>";
        var entries=new LinkedHashMap<String,byte[]>();entries.put("[Content_Types].xml",text(types));entries.put("_rels/.rels",text("<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                +"<Relationship Id='main' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument' Target='word/document.xml'/></Relationships>"));
        entries.put("word/document.xml",text("<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:body/></w:document>"));
        if(media!=null)entries.put("word/media/image.png",media);return zip(entries);
    }
    @Test void officeCannotPassOnPackageStructureWhileHidingAnInvalidMediaPart()throws Exception{
        assertThat(run(FileFormat.DOCX,docx(null)).report.verdict()).isEqualTo(Verdict.PASS);
        assertThat(run(FileFormat.DOCX,docx(png())).report.verdict()).isEqualTo(Verdict.PASS);
        assertThat(run(FileFormat.DOCX,docx(text("fake image"))).report.verdict()).isEqualTo(Verdict.REJECT);
        assertThat(run(FileFormat.ZIP,zip(Map.of("document.docx",docx(text("fake image"))))).report.verdict()).isEqualTo(Verdict.REJECT);
    }
    @Test void nestedDepthEntryCountAndAggregateBytesShareOneBudget()throws Exception{
        byte[] leaf=zip(Map.of("notes.txt",text("content"))),one=zip(Map.of("nested.zip",leaf)),two=zip(Map.of("nested.zip",one)),three=zip(Map.of("nested.zip",two));
        assertThat(run(FileFormat.ZIP,two).report.verdict()).isEqualTo(Verdict.PASS);
        assertThat(run(FileFormat.ZIP,three).report.code()).isEqualTo(Code.CAPACITY);
        var members=new LinkedHashMap<String,byte[]>();for(int i=0;i<600;i++)members.put("entry"+i+".txt",new byte[0]);byte[] many=zip(members);
        assertThat(run(FileFormat.ZIP,zip(Map.of("first.zip",many,"second.zip",many))).report.code()).isEqualTo(Code.CAPACITY);
        assertThat(run(FileFormat.ZIP,zip(Map.of("file.txt",text("1234567890"))),9).report.code()).isEqualTo(Code.CAPACITY);
        long outerPayload=leaf.length;assertThat(run(FileFormat.ZIP,one,outerPayload).report.code()).isEqualTo(Code.CAPACITY);
    }
    @Test void invalidVideoMemberCannotReceivePartialPass()throws Exception{
        for(String name:List.of("video.mp4")){
            var result=run(FileFormat.ZIP,zip(Map.of("good.txt",text("ok"),name,text("payload"))));
            assertThat(result.report.verdict()).isEqualTo(Verdict.REJECT);
        }
    }
    @Test void realWorkerProcessValidatesNestedContentWithinItsPrivateScratchDirectory()throws Exception{
        var prepared=run(FileFormat.ZIP,zip(Map.of("document.docx",docx(png()),"notes.txt",text("notes"))));
        var command=List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),
                "-Xmx256m","-XX:MaxDirectMemorySize=16m","-XX:ActiveProcessorCount=1","-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),ContentInspectionWorker.class.getName(),prepared.source.toString(),prepared.scratch.toString());
        try(var process=new BoundedInspectionProcess(1)){
            var result=process.execute(command,directory,mapper().writeValueAsBytes(prepared.request),Duration.ofSeconds(20));assertThat(result.exitCode()).isZero();
            assertThat(verify(prepared.request,result.output()).verdict()).isEqualTo(Verdict.PASS);
        }
        try(var files=Files.list(prepared.scratch)){assertThat(files.toList()).isEmpty();}
    }
}
