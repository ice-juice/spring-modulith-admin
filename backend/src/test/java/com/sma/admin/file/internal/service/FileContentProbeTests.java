package com.sma.admin.file.internal.service;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.sma.admin.file.FileFormat;
import com.sma.admin.file.internal.service.FileContentProbe.*;
import org.junit.jupiter.api.Test;

class FileContentProbeTests {
    private final Limits limits=new Limits(5L*1024*1024,Duration.ofSeconds(5));
    private String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private byte[] ascii(String value){return value.getBytes(StandardCharsets.US_ASCII);}
    private Candidate inspect(FileFormat format,byte[] bytes)throws Exception{return FileContentProbe.inspect(new ByteArrayInputStream(bytes),format,bytes.length,hash(bytes),limits);}
    private void rejected(FileFormat format,byte[] bytes,Reason reason){assertThatThrownBy(()->inspect(format,bytes)).isInstanceOfSatisfying(Rejected.class,e->assertThat(e.reason()).isEqualTo(reason));}
    @Test void signaturesAreOnlyPreliminaryAndOfficeStillRequiresContainerValidation()throws Exception{
        var headers=Map.of(FileFormat.PNG,new byte[]{(byte)137,80,78,71,13,10,26,10},FileFormat.JPEG,new byte[]{(byte)255,(byte)216,(byte)255},
                FileFormat.GIF,ascii("GIF89a"),FileFormat.PDF,ascii("%PDF-1.7\n"),FileFormat.ZIP,new byte[]{80,75,3,4});
        for(var entry:headers.entrySet())assertThat(inspect(entry.getKey(),entry.getValue()).claimedFormat()).isEqualTo(entry.getKey());
        for(var office:List.of(FileFormat.DOCX,FileFormat.XLSX,FileFormat.PPTX))assertThat(inspect(office,new byte[]{80,75,3,4}).signature()).isEqualTo(Signature.ZIP);
        rejected(FileFormat.PNG,ascii("%PDF-1.7\n"),Reason.TYPE);rejected(FileFormat.PDF,ascii("notes"),Reason.TYPE);
    }
    @Test void webpLengthAndMp4BrandBoundsAreCheckedBeforeComplexParsing()throws Exception{
        byte[] webp=new byte[]{82,73,70,70,8,0,0,0,87,69,66,80,86,80,56,32};assertThat(inspect(FileFormat.WEBP,webp).signature()).isEqualTo(Signature.WEBP);
        webp[4]=9;rejected(FileFormat.WEBP,webp,Reason.TYPE);
        byte[] mp4=new byte[]{0,0,0,16,102,116,121,112,105,115,111,109,0,0,0,0};assertThat(inspect(FileFormat.MP4,mp4).signature()).isEqualTo(Signature.ISO_BMFF);
        mp4[3]=20;rejected(FileFormat.MP4,mp4,Reason.TYPE);mp4[3]=16;System.arraycopy(ascii("heic"),0,mp4,8,4);rejected(FileFormat.MP4,mp4,Reason.TYPE);
        rejected(FileFormat.PDF,ascii("%PDF-1.8\n"),Reason.TYPE);rejected(FileFormat.PDF,ascii("%PDF-1.7x"),Reason.TYPE);
    }
    @Test void plainTextAndUnicodeAreCheckedAcrossBufferAndSingleByteReads()throws Exception{
        byte[] bytes=("a".repeat(65535)+"你好😀\r\n\t"+"b".repeat(65537)).getBytes(StandardCharsets.UTF_8);
        assertThat(inspect(FileFormat.TEXT,bytes).signature()).isEqualTo(Signature.UTF8_TEXT);
        try(var one=new ByteArrayInputStream(bytes){@Override public synchronized int read(byte[] b,int o,int n){return super.read(b,o,Math.min(n,1));}}){
            assertThat(FileContentProbe.inspect(one,FileFormat.TEXT,bytes.length,hash(bytes),limits).size()).isEqualTo(bytes.length);
        }
        for(byte[] invalid:List.of(new byte[]{(byte)0xc0,(byte)0x80},new byte[]{(byte)0xed,(byte)0xa0,(byte)0x80},new byte[]{(byte)0xf4,(byte)0x90,(byte)0x80,(byte)0x80},
                new byte[]{(byte)0xe4,(byte)0xbd},new byte[]{(byte)0xff},new byte[]{(byte)0x80}))rejected(FileFormat.TEXT,invalid,Reason.UTF8);
        rejected(FileFormat.TEXT,new byte[]{97,0,98},Reason.CONTROL_CHARACTER);rejected(FileFormat.CSV,new byte[]{97,27,98},Reason.CONTROL_CHARACTER);
    }
    @Test void knownExecutableAndActiveMarkupPrefixesCannotBeRenamedAsText(){
        for(String executable:List.of("MZ renamed binary","#!/bin/sh\nrun","  <?php echo 'x';","\n<svg xmlns='example'>","<html>active","<script>alert(1)</script>"))
            rejected(FileFormat.TEXT,ascii(executable),Reason.EXECUTABLE_PREFIX);
        rejected(FileFormat.ZIP,new byte[]{127,69,76,70},Reason.EXECUTABLE_PREFIX);
    }
    @Test void entireStreamDigestAndSizeMatterNotOnlyItsHeader()throws Exception{
        byte[] bytes=new byte[2*1024*1024+3];Arrays.fill(bytes,(byte)'a');String expected=hash(bytes);bytes[bytes.length-1]='b';
        assertThatThrownBy(()->FileContentProbe.inspect(new ByteArrayInputStream(bytes),FileFormat.TEXT,bytes.length,expected,limits))
                .isInstanceOfSatisfying(Rejected.class,e->assertThat(e.reason()).isEqualTo(Reason.CHECKSUM));
        for(long size:List.of((long)bytes.length-1,(long)bytes.length+1))assertThatThrownBy(()->FileContentProbe.inspect(new ByteArrayInputStream(bytes),FileFormat.TEXT,size,hash(bytes),limits))
                .isInstanceOfSatisfying(Rejected.class,e->assertThat(e.reason()).isEqualTo(Reason.SIZE));
    }
    @Test void oversizedOrInterruptedSourcesCloseAndNeverAllocateAnUnboundedRead()throws Exception{
        var closed=new AtomicBoolean();var reads=new ArrayList<Integer>();byte[] bytes=ascii("bounded stream");
        var source=new ByteArrayInputStream(bytes){@Override public synchronized int read(byte[] b,int o,int n){reads.add(n);return super.read(b,o,n);}@Override public void close(){closed.set(true);}};
        assertThatThrownBy(()->FileContentProbe.inspect(source,FileFormat.TEXT,3,hash(bytes),limits)).isInstanceOf(Rejected.class);
        assertThat(closed).isTrue();assertThat(reads).containsExactly(4);
        Thread.currentThread().interrupt();try{assertThatThrownBy(()->inspect(FileFormat.TEXT,bytes)).isInstanceOf(InterruptedIOException.class);assertThat(Thread.currentThread().isInterrupted()).isTrue();}
        finally{Thread.interrupted();}
    }
    @Test void stalledNonblockingSourceHitsDeadlineAndIoFailureIsNotClean()throws Exception{
        var emptyProgress=new InputStream(){public int read(){return 0;}@Override public int read(byte[] b,int o,int n){return 0;}};
        assertThatThrownBy(()->FileContentProbe.inspect(emptyProgress,FileFormat.TEXT,1,"a".repeat(64),new Limits(10,Duration.ofMillis(5))))
                .isInstanceOfSatisfying(Rejected.class,e->assertThat(e.reason()).isEqualTo(Reason.DEADLINE));
        var broken=new InputStream(){public int read()throws IOException{throw new IOException("read failed");}};
        assertThatThrownBy(()->FileContentProbe.inspect(broken,FileFormat.TEXT,1,"a".repeat(64),limits)).isInstanceOf(IOException.class);
    }
    @Test void deadlineIsRecheckedAfterABlockingEofReadReturns()throws Exception{
        var slowEof=new ByteArrayInputStream(ascii("a")){
            @Override public synchronized int read(byte[] b,int offset,int length){
                if(available()==0)try{Thread.sleep(30);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
                return super.read(b,offset,length);
            }
        };
        assertThatThrownBy(()->FileContentProbe.inspect(slowEof,FileFormat.TEXT,1,hash(ascii("a")),new Limits(10,Duration.ofMillis(10))))
                .isInstanceOfSatisfying(Rejected.class,e->assertThat(e.reason()).isEqualTo(Reason.DEADLINE));
    }
    @Test void textCandidateIsNotAJsonCsvStructureOrMalwareVerdict()throws Exception{
        assertThat(inspect(FileFormat.JSON,ascii("not JSON yet")).signature()).isEqualTo(Signature.UTF8_TEXT);
        assertThat(inspect(FileFormat.CSV,ascii("=SUM(1,2)\n")).signature()).isEqualTo(Signature.UTF8_TEXT);
        // The standard antivirus test marker is ASCII too. A type/hash preflight must never claim to replace antivirus.
        String marker="X5O!P%@AP[4\\PZX54(P^)7CC)7}$"+"EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        assertThat(inspect(FileFormat.TEXT,ascii(marker)).signature()).isEqualTo(Signature.UTF8_TEXT);
    }
}
