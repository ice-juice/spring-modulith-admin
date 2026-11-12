package com.sma.admin.file.internal.inspection;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import com.sma.admin.file.FileFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VideoContentInspectionTests {
    @TempDir Path directory;
    static byte[] fixture(String name)throws Exception{try(var input=VideoContentInspectionTests.class.getResourceAsStream("/file-inspection/"+name)){assertThat(input).isNotNull();return input.readAllBytes();}}
    private Path file(byte[] bytes)throws Exception{Path path=directory.resolve(UUID.randomUUID()+".mp4");Files.write(path,bytes);return path;}
    private int find(byte[] bytes,String text){byte[] wanted=text.getBytes(StandardCharsets.US_ASCII);outer:for(int i=0;i<=bytes.length-wanted.length;i++){for(int n=0;n<wanted.length;n++)if(bytes[i+n]!=wanted[n])continue outer;return i;}throw new AssertionError("Missing box "+text);}
    @Test void validMovieEnvelopesAreAcceptedButExternalReferencesAndInvalidIndexesAreRejected()throws Exception{
        for(String name:List.of("synthetic-video.mp4","synthetic-audio-video.mp4"))Mp4ContainerInspection.inspect(file(fixture(name)));
        byte[] external=fixture("synthetic-video.mp4");int reference=find(external,"url ");external[reference+7]=0;
        assertThatThrownBy(()->Mp4ContainerInspection.inspect(file(external))).isInstanceOf(Mp4ContainerInspection.Rejected.class);
        byte[] index=fixture("synthetic-video.mp4");int description=find(index,"stsd");assertThat(new String(index,description+16,4,StandardCharsets.US_ASCII)).isEqualTo("avc1");
        ByteBuffer.wrap(index,description+26,2).putShort((short)2);assertThatThrownBy(()->Mp4ContainerInspection.inspect(file(index))).isInstanceOf(Mp4ContainerInspection.Rejected.class);
    }
    @Test void truncatedOversizedOrTrailingAtomsAndFragmentedMoviesAreRejected()throws Exception{
        byte[] original=fixture("synthetic-video.mp4"),tail=Arrays.copyOf(original,original.length+8);System.arraycopy("<?php ?>".getBytes(StandardCharsets.US_ASCII),0,tail,original.length,8);
        for(byte[] bytes:List.of(tail,Arrays.copyOf(original,original.length-1)))assertThatThrownBy(()->Mp4ContainerInspection.inspect(file(bytes))).isInstanceOf(IOException.class);
        byte[] invalid=original.clone();ByteBuffer.wrap(invalid,find(invalid,"moov")-4,4).putInt(Integer.MAX_VALUE);assertThatThrownBy(()->Mp4ContainerInspection.inspect(file(invalid))).isInstanceOf(IOException.class);
        byte[] fragmented=original.clone();System.arraycopy("moof".getBytes(StandardCharsets.US_ASCII),0,fragmented,find(fragmented,"moov"),4);
        assertThatThrownBy(()->Mp4ContainerInspection.inspect(file(fragmented))).isInstanceOf(IOException.class);
        var large=directory.resolve("large.mp4");try(var out=new RandomAccessFile(large.toFile(),"rw")){out.writeInt(17*1024*1024+8);out.writeBytes("moov");out.setLength(17*1024*1024+8);}
        assertThatThrownBy(()->Mp4ContainerInspection.inspect(large)).isInstanceOf(Mp4ContainerInspection.Capacity.class);
    }
    private String report(){return "{\"streams\":[{\"index\":0,\"codec_type\":\"video\",\"codec_name\":\"h264\",\"width\":64,\"height\":48,\"r_frame_rate\":\"10/1\",\"avg_frame_rate\":\"10/1\",\"disposition\":{\"attached_pic\":0}}],"
            +"\"format\":{\"format_name\":\"mov,mp4,m4a,3gp,3g2,mj2\",\"duration\":\"1.000000\",\"size\":\"1021\"}}";}
    @Test void probeReportsEnforceBoundedCodecsDimensionsRateDurationAndSourceSize()throws Exception{
        VideoContentInspection.verify(report().getBytes(StandardCharsets.UTF_8),1021);
        for(String invalid:List.of(report().replace("h264","mpeg4"),report().replace("64,","9000,"),report().replace("10/1","120/1"),report().replace("10/1","0/0"),
                report().replace("1.000000","21601"),report().replace("1.000000","NaN"),report().replace("1021","999"),report().replace("attached_pic\":0","attached_pic\":1"),
                report().replace("video","subtitle"),report().replace("mov,mp4,m4a,3gp,3g2,mj2","hls"),report()+"{}"))
            assertThatThrownBy(()->VideoContentInspection.verify(invalid.getBytes(StandardCharsets.UTF_8),1021)).isInstanceOf(IOException.class);
    }
    @Test void nativeArgumentsCannotEnableNetworkExternalFilesOrASecondInput(){
        Path executable=directory.resolve("probe tools/ffprobe.exe"),source=directory.resolve("clip ; echo.mp4");var command=VideoContentInspection.command(executable,source);
        assertThat(command.getFirst()).isEqualTo(executable.toString());assertThat(command.getLast()).isEqualTo(source.toString());assertThat(Collections.frequency(command,"-i")).isEqualTo(1);
        for(var flag:Map.of("-protocol_whitelist","file","-format_whitelist","mov","-enable_drefs","0","-use_absolute_path","0","-threads","1","-max_streams","8").entrySet())
            assertThat(command.get(command.indexOf(flag.getKey())+1)).isEqualTo(flag.getValue());
        assertThatThrownBy(()->VideoContentInspection.command(Path.of("relative"),source)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void absentNativeExecutableIsExplicitErrorAndCannotPublishAMovie()throws Exception{
        String previous=System.getProperty("sma.file.worker.ffprobe");System.setProperty("sma.file.worker.ffprobe",directory.resolve("missing-probe.exe").toString());
        try{
            byte[] bytes=fixture("synthetic-video.mp4");var input=file(bytes);String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            var result=ContentInspectionWorker.inspect(input,directory,new InspectionProtocol.Request(1,FileFormat.MP4,bytes.length,checksum,0));
            assertThat(result.verdict()).isEqualTo(InspectionProtocol.Verdict.ERROR);assertThat(result.code()).isEqualTo(InspectionProtocol.Code.PROCESSOR_UNAVAILABLE);
        }finally{if(previous==null)System.clearProperty("sma.file.worker.ffprobe");else System.setProperty("sma.file.worker.ffprobe",previous);}
    }
}
