package com.sma.admin.file.internal.inspection;

import static org.assertj.core.api.Assertions.*;
import static com.sma.admin.file.internal.inspection.InspectionProtocol.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.zip.CRC32;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import com.sma.admin.file.FileFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageContentInspectionTests {
    @TempDir Path directory;
    private record Run(Report report,Path source,Path output,Request request){}
    private String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private byte[] raster(String format)throws Exception{
        var image=new BufferedImage(8,6,format.equals("jpeg")?BufferedImage.TYPE_INT_RGB:BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<6;y++)for(int x=0;x<8;x++)image.setRGB(x,y,0xff000000|x*30<<16|y*40<<8|100);
        var out=new ByteArrayOutputStream();assertThat(ImageIO.write(image,format,out)).isTrue();return out.toByteArray();
    }
    private Run run(FileFormat format,byte[] bytes,long budget)throws Exception{
        Path source=directory.resolve(UUID.randomUUID()+".blob"),output=Files.createDirectory(directory.resolve(UUID.randomUUID().toString()));Files.write(source,bytes);
        var request=new Request(1,format,bytes.length,hash(bytes),budget);return new Run(ContentInspectionWorker.inspect(source,output,request),source,output,request);
    }
    private Run run(FileFormat format,byte[] bytes)throws Exception{return run(format,bytes,1024*1024);}
    private void normalized(Run run)throws Exception{
        var report=verify(run.request,mapper().writeValueAsBytes(run.report));assertThat(report.verdict()).isEqualTo(Verdict.PASS);var result=report.normalized();
        assertThat(result.name()).isEqualTo("normalized.png");assertThat(result.format()).isEqualTo(FileFormat.PNG);
        Path output=run.output.resolve(result.name());byte[] bytes=Files.readAllBytes(output);assertThat(result.checksum()).isEqualTo(hash(bytes));assertThat(result.size()).isEqualTo(bytes.length);
        var decoded=ImageIO.read(output.toFile());assertThat(decoded.getWidth()).isEqualTo(8);assertThat(decoded.getHeight()).isEqualTo(6);
        var original=ImageIO.read(run.source.toFile());assertThat(decoded.getRGB(0,0,8,6,null,0,8)).containsExactly(original.getRGB(0,0,8,6,null,0,8));
        ImageContainerInspection.inspect(output,FileFormat.PNG);
    }
    @Test void pngJpegGifAndBothWebpCodecsProduceNewStaticPngBytes()throws Exception{
        for(var pair:Map.of(FileFormat.PNG,"png",FileFormat.JPEG,"jpeg",FileFormat.GIF,"gif").entrySet())normalized(run(pair.getKey(),raster(pair.getValue())));
        for(String name:List.of("synthetic-lossless.webp","synthetic-lossy.webp","synthetic-alpha.webp"))normalized(run(FileFormat.WEBP,fixture(name)));
    }
    private byte[] fixture(String name)throws Exception{try(var input=getClass().getResourceAsStream("/file-inspection/"+name)){assertThat(input).isNotNull();return input.readAllBytes();}}
    @Test void trailingPayloadAndTruncationCannotBeAcceptedByLenientDecoders()throws Exception{
        var inputs=new EnumMap<FileFormat,byte[]>(FileFormat.class);inputs.put(FileFormat.PNG,raster("png"));inputs.put(FileFormat.JPEG,raster("jpeg"));inputs.put(FileFormat.GIF,raster("gif"));inputs.put(FileFormat.WEBP,fixture("synthetic-lossless.webp"));
        for(var entry:inputs.entrySet()){
            byte[] original=entry.getValue(),extra=Arrays.copyOf(original,original.length+8);System.arraycopy("<?php ?>".getBytes(StandardCharsets.US_ASCII),0,extra,original.length,8);
            assertThat(run(entry.getKey(),extra).report.verdict()).isNotEqualTo(Verdict.PASS);
            assertThat(run(entry.getKey(),Arrays.copyOf(original,original.length-1)).report.verdict()).isNotEqualTo(Verdict.PASS);
        }
    }
    private byte[] pngChunk(String name,byte[] content)throws Exception{
        byte[] type=name.getBytes(StandardCharsets.US_ASCII);var crc=new CRC32();crc.update(type);crc.update(content);var buffer=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(buffer)){out.writeInt(content.length);out.write(type);out.write(content);out.writeInt((int)crc.getValue());}return buffer.toByteArray();
    }
    private byte[] insertChunk(byte[] png,String type,byte[] content)throws Exception{
        byte[] chunk=pngChunk(type,content);var out=new ByteArrayOutputStream();out.write(png,0,33);out.write(chunk);out.write(png,33,png.length-33);return out.toByteArray();
    }
    @Test void pngMetadataIsDroppedAndCrcOrAnimationChunksAreRejected()throws Exception{
        byte[] metadata="Comment\0sensitive-location-marker".getBytes(StandardCharsets.US_ASCII);var result=run(FileFormat.PNG,insertChunk(raster("png"),"tEXt",metadata));normalized(result);
        assertThat(new String(Files.readAllBytes(result.output.resolve("normalized.png")),StandardCharsets.ISO_8859_1)).doesNotContain("sensitive-location-marker","tEXt");
        byte[] broken=raster("png");broken[29]^=1;assertThat(run(FileFormat.PNG,broken).report.verdict()).isEqualTo(Verdict.REJECT);
        assertThat(run(FileFormat.PNG,insertChunk(raster("png"),"acTL",new byte[]{0,0,0,2,0,0,0,0})).report.verdict()).isEqualTo(Verdict.REJECT);
        assertThat(run(FileFormat.WEBP,fixture("synthetic-animated.webp")).report.verdict()).isEqualTo(Verdict.REJECT);
    }
    @Test void pixelBombIsRejectedBeforeAllocationAndOutputCannotExceedBudgetOrOverwrite()throws Exception{
        byte[] png=raster("png");ByteBuffer.wrap(png,16,4).putInt(9000);var crc=new CRC32();crc.update(png,12,17);ByteBuffer.wrap(png,29,4).putInt((int)crc.getValue());
        assertThat(run(FileFormat.PNG,png).report.code()).isEqualTo(Code.CAPACITY);
        var small=run(FileFormat.PNG,raster("png"),12);assertThat(small.report.verdict()).isEqualTo(Verdict.ERROR);assertThat(small.report.code()).isEqualTo(Code.CAPACITY);
        try(var files=Files.list(small.output)){for(var path:files.toList())assertThat(Files.size(path)).isLessThanOrEqualTo(12);}
        var first=run(FileFormat.PNG,raster("png"));normalized(first);byte[] before=Files.readAllBytes(first.output.resolve("normalized.png"));
        assertThat(ContentInspectionWorker.inspect(first.source,first.output,first.request).code()).isEqualTo(Code.IO);
        assertThat(Files.readAllBytes(first.output.resolve("normalized.png"))).containsExactly(before);
        assertThat(run(FileFormat.PNG,raster("png"),0).report.code()).isEqualTo(Code.CAPACITY);
    }
    private byte[] gif(int frames)throws Exception{
        var output=new ByteArrayOutputStream();var writer=ImageIO.getImageWritersByFormatName("gif").next();
        try(var sink=new MemoryCacheImageOutputStream(output)){
            writer.setOutput(sink);writer.prepareWriteSequence(null);
            for(int i=0;i<frames;i++){var image=new BufferedImage(8,6,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,0xff000000|i);writer.writeToSequence(new IIOImage(image,null,null),null);}
            writer.endWriteSequence();sink.flush();
        }finally{writer.dispose();}return output.toByteArray();
    }
    @Test void animatedGifValidatesEveryFrameAndCapsFrameCount()throws Exception{
        normalized(run(FileFormat.GIF,gif(2)));assertThat(run(FileFormat.GIF,gif(33)).report.verdict()).isEqualTo(Verdict.REJECT);
        byte[] truncated=gif(2);truncated[truncated.length-3]=(byte)255;assertThat(run(FileFormat.GIF,truncated).report.verdict()).isNotEqualTo(Verdict.PASS);
    }
    @Test void actualWorkerProcessDecodesWebpAndProducesBoundedPrivateDerivative()throws Exception{
        byte[] bytes=fixture("synthetic-alpha.webp");Path source=directory.resolve("input.blob"),output=Files.createDirectory(directory.resolve("worker-output"));Files.write(source,bytes);
        var request=new Request(1,FileFormat.WEBP,bytes.length,hash(bytes),1048576);
        var command=List.of(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),
                "-Xmx256m","-XX:MaxDirectMemorySize=16m","-XX:ActiveProcessorCount=1","-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),ContentInspectionWorker.class.getName(),source.toString(),output.toString());
        try(var runner=new BoundedInspectionProcess(1)){
            var result=runner.execute(command,directory,mapper().writeValueAsBytes(request),Duration.ofSeconds(20));assertThat(result.exitCode()).isZero();
            var report=verify(request,result.output());normalized(new Run(report,source,output,request));
        }
    }
}
