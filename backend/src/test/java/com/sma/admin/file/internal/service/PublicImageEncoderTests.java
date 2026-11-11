package com.sma.admin.file.internal.service;
import static org.assertj.core.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
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
}
