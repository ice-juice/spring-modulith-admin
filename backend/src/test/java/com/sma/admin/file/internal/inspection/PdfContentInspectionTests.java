package com.sma.admin.file.internal.inspection;

import static org.assertj.core.api.Assertions.*;
import static com.sma.admin.file.internal.inspection.InspectionProtocol.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import com.sma.admin.file.FileFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfContentInspectionTests {
    @TempDir Path directory;
    @FunctionalInterface interface Edit {void apply(PDDocument document)throws Exception;}
    private byte[] pdf(boolean compressed,Edit edit)throws Exception{
        try(var document=new PDDocument();var bytes=new ByteArrayOutputStream()){
            document.addPage(new PDPage());edit.apply(document);document.save(bytes,compressed?CompressParameters.DEFAULT_COMPRESSION:CompressParameters.NO_COMPRESSION);return bytes.toByteArray();
        }
    }
    private byte[] pdf(Edit edit)throws Exception{return pdf(true,edit);}
    private String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private record Run(Report report,Request request,Path source,Path output){}
    private Run run(FileFormat format,byte[] bytes)throws Exception{
        var source=directory.resolve(UUID.randomUUID()+".blob");Files.write(source,bytes);var output=Files.createDirectory(directory.resolve(UUID.randomUUID().toString()));
        var request=new Request(1,format,bytes.length,hash(bytes),64L*1024*1024);return new Run(ContentInspectionWorker.inspect(source,output,request),request,source,output);
    }
    private Run run(byte[] bytes)throws Exception{return run(FileFormat.PDF,bytes);}
    private void content(PDDocument doc,String text)throws Exception{doc.getPage(0).setContents(new PDStream(doc,new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII)),COSName.FLATE_DECODE));}
    @Test void strictParserAcceptsOrdinaryClassicAndObjectStreamPdf()throws Exception{
        for(boolean compressed:List.of(true,false)){
            var result=run(pdf(compressed,doc->content(doc,"q 1 0 0 1 0 0 cm Q\n")));
            assertThat(result.report.verdict()).isEqualTo(Verdict.PASS);assertThat(verify(result.request,mapper().writeValueAsBytes(result.report))).isEqualTo(result.report);
        }
    }
    @Test void actionsScriptsAttachmentsFormsAndExternalStreamsAreRejected()throws Exception{
        for(String key:List.of("OpenAction","AA","JavaScript","EmbeddedFiles","AF","AcroForm","XFA","RichMediaContent")){
            var result=run(pdf(doc->doc.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName(key),new COSDictionary())));
            assertThat(result.report.verdict()).as(key).isEqualTo(Verdict.REJECT);
        }
        var recursive=run(pdf(doc->{var hidden=new COSDictionary();hidden.setString(COSName.JS,"app.alert('hidden')");doc.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName("Custom"),hidden);}));
        assertThat(recursive.report.verdict()).isEqualTo(Verdict.REJECT);
        var file=run(pdf(doc->{var stream=new PDStream(doc,new ByteArrayInputStream(new byte[]{1,2,3}));stream.getCOSObject().setString(COSName.F,"https://external.invalid/file");doc.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName("Custom"),stream);}));
        assertThat(file.report.verdict()).isEqualTo(Verdict.REJECT);
    }
    @Test void encryptedIncludingEmptyUserPasswordCannotPass()throws Exception{
        for(String password:List.of("", "protected")){
            var result=run(pdf(doc->{var policy=new StandardProtectionPolicy("owner",password,new AccessPermission());policy.setEncryptionKeyLength(128);doc.protect(policy);}));
            assertThat(result.report.verdict()).isEqualTo(Verdict.REJECT);
        }
    }
    @Test void corruptTruncatedOrTrailingBytesNeverReceiveACleanVerdict()throws Exception{
        byte[] valid=pdf(doc->{});var trailing=Arrays.copyOf(valid,valid.length+8);System.arraycopy("<?php ?>".getBytes(StandardCharsets.US_ASCII),0,trailing,valid.length,8);
        for(byte[] bytes:List.of(trailing,Arrays.copyOf(valid,valid.length-6),"%PDF-1.7\nno real objects\n%%EOF\n".getBytes(StandardCharsets.US_ASCII)))
            assertThat(run(bytes).report.verdict()).isEqualTo(Verdict.REJECT);
    }
    @Test void decodedStreamsPixelDeclarationsAndUnknownFiltersCannotBypassLimits()throws Exception{
        var huge=run(pdf(doc->{var stream=new PDStream(doc);try(var out=stream.createOutputStream(COSName.FLATE_DECODE)){byte[] block=new byte[65536];for(int i=0;i<257;i++)out.write(block);}doc.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName("Custom"),stream);}));
        assertThat(huge.report.verdict()).isEqualTo(Verdict.ERROR);assertThat(huge.report.code()).isEqualTo(Code.CAPACITY);
        var image=run(pdf(doc->{var stream=new PDStream(doc,new ByteArrayInputStream(new byte[]{1}));stream.getCOSObject().setName(COSName.SUBTYPE,"Image");stream.getCOSObject().setInt(COSName.WIDTH,9000);stream.getCOSObject().setInt(COSName.HEIGHT,2);doc.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName("Custom"),stream);}));
        assertThat(image.report.code()).isEqualTo(Code.CAPACITY);
        var unknown=run(pdf(doc->{var stream=new PDStream(doc,new ByteArrayInputStream(new byte[]{1}));stream.getCOSObject().setName(COSName.FILTER,"UnknownDecode");doc.getDocumentCatalog().getCOSObject().setItem(COSName.getPDFName("Custom"),stream);}));
        assertThat(unknown.report.code()).isEqualTo(Code.PROCESSOR_UNAVAILABLE);
    }
    private byte[] archive(byte[] pdf)throws Exception{var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("document.pdf"));zip.write(pdf);zip.closeEntry();}return bytes.toByteArray();}
    @Test void pdfInsideZipUsesTheSameFullChecksAndOneBadFileRejectsArchive()throws Exception{
        assertThat(run(FileFormat.ZIP,archive(pdf(doc->{}))).report.verdict()).isEqualTo(Verdict.PASS);
        assertThat(run(FileFormat.ZIP,archive(pdf(doc->doc.getDocumentCatalog().getCOSObject().setString(COSName.JS,"payload")))).report.verdict()).isEqualTo(Verdict.REJECT);
    }
    @Test void realRestrictedJvmProducesOnlyTheBoundReportForPdf()throws Exception{
        var prepared=run(pdf(doc->content(doc,"q Q\n")));
        var command=List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"-Xmx256m","-XX:MaxDirectMemorySize=16m","-XX:ActiveProcessorCount=1",
                "-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),ContentInspectionWorker.class.getName(),prepared.source.toString(),prepared.output.toString());
        try(var runner=new BoundedInspectionProcess(1)){
            var result=runner.execute(command,directory,mapper().writeValueAsBytes(prepared.request),Duration.ofSeconds(20));assertThat(result.exitCode()).isZero();
            assertThat(verify(prepared.request,result.output()).verdict()).isEqualTo(Verdict.PASS);
        }
    }
    private byte[] unreachableObject(String object)throws Exception{
        var output=new ByteArrayOutputStream();output.write("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));var offsets=new ArrayList<Integer>();
        var objects=List.of("<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>",object);
        for(int i=0;i<objects.size();i++){offsets.add(output.size());output.write(((i+1)+" 0 obj\n"+objects.get(i)+"\nendobj\n").getBytes(StandardCharsets.US_ASCII));}
        int xref=output.size();output.write("xref\n0 5\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for(int offset:offsets)output.write(String.format(Locale.ROOT,"%010d 00000 n \n",offset).getBytes(StandardCharsets.US_ASCII));
        output.write(("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));return output.toByteArray();
    }
    @Test void unreachableIndexedObjectsAndEscapedPdfNamesAreStillInspected()throws Exception{
        assertThat(run(unreachableObject("<< /Safe (ordinary unused object) >>")).report.verdict()).isEqualTo(Verdict.PASS);
        assertThat(run(unreachableObject("<< /J#53 (hidden javascript) >>")).report.verdict()).isEqualTo(Verdict.REJECT);
    }
}
