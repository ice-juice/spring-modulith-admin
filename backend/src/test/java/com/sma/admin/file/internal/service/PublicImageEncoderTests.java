package com.sma.admin.file.internal.service;
import static org.assertj.core.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.spi.*;
import javax.imageio.stream.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.admin.common.BizException;
class PublicImageEncoderTests {
    @TempDir Path directory;
    @Test void publicationIsAStaticPngWithoutTrailingPayloadAndBytesAreDefensive() throws Exception {
        var path=directory.resolve("image.blob");var pixels=new BufferedImage(32,20,BufferedImage.TYPE_INT_ARGB);pixels.setRGB(2,2,0xff00aa22);
        ImageIO.write(pixels,"png",path.toFile());Files.writeString(path,"<script>private metadata</script>",StandardOpenOption.APPEND);
        var snapshot=new PublicImageEncoder().encode(path);
        assertThat(snapshot.contentType()).isEqualTo("image/png");assertThat(snapshot.width()).isEqualTo(32);assertThat(snapshot.height()).isEqualTo(20);
        assertThat(new String(snapshot.bytes(),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("private metadata");
        var copy=snapshot.bytes();copy[0]=0;assertThat(snapshot.bytes()[0]).isNotZero();
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(snapshot.bytes())).getRGB(2,2)).isEqualTo(0xff00aa22);
    }
    @Test void invalidRasterAndOversizedDimensionsOrFilesAreRejected() throws Exception {
        var encoder=new PublicImageEncoder();var fake=directory.resolve("fake.png");Files.writeString(fake,"<svg onload='bad()'/>");
        assertThatThrownBy(()->encoder.encode(fake)).isInstanceOf(BizException.class);
        var big=directory.resolve("large.png");ImageIO.write(new BufferedImage(4097,1,BufferedImage.TYPE_INT_RGB),"png",big.toFile());
        assertThatThrownBy(()->encoder.encode(big)).isInstanceOf(BizException.class);
        Files.write(fake,new byte[PublicImageEncoder.MAX_BYTES+1]);assertThatThrownBy(()->encoder.encode(fake)).isInstanceOf(BizException.class);
    }
    @Test void onlyJdkRasterReadersAreUsedAndEveryAcceptedFormatBecomesStaticPng() throws Exception {
        var encoder=new PublicImageEncoder();
        for(String format:List.of("png","jpeg","gif")) {
            var path=directory.resolve(format+".blob");var raster=new BufferedImage(24,16,BufferedImage.TYPE_INT_RGB);raster.setRGB(3,4,0xff224466);
            assertThat(ImageIO.write(raster,format,path.toFile())).isTrue();
            var result=encoder.encode(path);assertThat(result.contentType()).isEqualTo("image/png");
            assertThat(result.bytes()).startsWith((byte)137,(byte)80,(byte)78,(byte)71);
            assertThat(ImageIO.read(new java.io.ByteArrayInputStream(result.bytes())).getWidth()).isEqualTo(24);
        }
        var unsupported=directory.resolve("pretend.png");
        for(String content:List.of("<svg><script>bad()</script></svg>","%PDF-1.7", "RIFF0000WEBP", "GIF90a", "MZprogram")) {
            Files.writeString(unsupported,content);assertThatThrownBy(()->encoder.encode(unsupported)).isInstanceOf(BizException.class);
        }
    }
    @Test void globallyRegisteredPluginsCannotProbeReadOrWritePublicAssets() throws Exception {
        var path=directory.resolve("safe.png");ImageIO.write(new BufferedImage(12,8,BufferedImage.TYPE_INT_RGB),"png",path.toFile());
        var registry=IIORegistry.getDefaultInstance();var reader=new UnapprovedReader();var writer=new UnapprovedWriter();
        registry.registerServiceProvider(reader,ImageReaderSpi.class);registry.registerServiceProvider(writer,ImageWriterSpi.class);
        try {
            var readers=registry.getServiceProviders(ImageReaderSpi.class,false);while(readers.hasNext()){var other=readers.next();if(other!=reader)registry.setOrdering(ImageReaderSpi.class,reader,other);}
            var writers=registry.getServiceProviders(ImageWriterSpi.class,false);while(writers.hasNext()){var other=writers.next();if(other!=writer)registry.setOrdering(ImageWriterSpi.class,writer,other);}
            var result=new PublicImageEncoder().encode(path);assertThat(result.width()).isEqualTo(12);
            assertThat(reader.calls.get()).isZero();assertThat(writer.calls.get()).isZero();
        } finally { registry.deregisterServiceProvider(reader,ImageReaderSpi.class);registry.deregisterServiceProvider(writer,ImageWriterSpi.class); }
    }
    @Test void compressedSourceCannotProduceAnUnboundedPngAndCapacityIsRecovered() throws Exception {
        var raster=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_RGB);var random=new Random(41012);
        for(int y=0;y<1024;y++)for(int x=0;x<1024;x++)raster.setRGB(x,y,random.nextInt(0x1000000));
        var source=directory.resolve("compressed.jpg");assertThat(ImageIO.write(raster,"jpeg",source.toFile())).isTrue();
        assertThat(Files.size(source)).isLessThan(PublicImageEncoder.MAX_BYTES);
        var encoder=new PublicImageEncoder();assertThatThrownBy(()->encoder.encode(source)).isInstanceOf(BizException.class);
        var small=directory.resolve("small.png");ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB),"png",small.toFile());
        for(int attempt=0;attempt<8;attempt++)assertThat(encoder.encode(small).width()).isEqualTo(8);
    }
    @Test void pngMetadataDoesNotSurvivePixelOnlyPublication() throws Exception {
        var source=directory.resolve("metadata.png");ImageIO.write(new BufferedImage(12,8,BufferedImage.TYPE_INT_RGB),"png",source.toFile());
        byte[] original=Files.readAllBytes(source);var out=new java.io.ByteArrayOutputStream();out.write(original,0,33);
        byte[] text="private\0not-for-publication".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var stream=new java.io.DataOutputStream(out);stream.writeInt(text.length);stream.writeBytes("tEXt");stream.write(text);
        var crc=new java.util.zip.CRC32();crc.update("tEXt".getBytes(java.nio.charset.StandardCharsets.US_ASCII));crc.update(text);stream.writeInt((int)crc.getValue());
        out.write(original,33,original.length-33);Files.write(source,out.toByteArray());
        var result=new PublicImageEncoder().encode(source);
        assertThat(new String(result.bytes(),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("not-for-publication","tEXt");
    }
    static final class UnapprovedReader extends ImageReaderSpi {
        final AtomicInteger calls=new AtomicInteger();
        UnapprovedReader(){super("test","1",new String[]{"png"},new String[]{"png"},new String[]{"image/png"},"UnapprovedReader",
                new Class<?>[]{ImageInputStream.class},null,false,null,null,null,null,false,null,null,null,null);}
        @Override public boolean canDecodeInput(Object source){calls.incrementAndGet();return true;}
        @Override public ImageReader createReaderInstance(Object extension){calls.incrementAndGet();throw new AssertionError("Unapproved reader invoked");}
        @Override public String getDescription(Locale locale){return "test-only forbidden reader";}
    }
    static final class UnapprovedWriter extends ImageWriterSpi {
        final AtomicInteger calls=new AtomicInteger();
        UnapprovedWriter(){super("test","1",new String[]{"png"},new String[]{"png"},new String[]{"image/png"},"UnapprovedWriter",
                new Class<?>[]{ImageOutputStream.class},null,false,null,null,null,null,false,null,null,null,null);}
        @Override public boolean canEncodeImage(ImageTypeSpecifier type){calls.incrementAndGet();return true;}
        @Override public ImageWriter createWriterInstance(Object extension){calls.incrementAndGet();throw new AssertionError("Unapproved writer invoked");}
        @Override public String getDescription(Locale locale){return "test-only forbidden writer";}
    }
}
