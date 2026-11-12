package com.sma.admin.file.internal.service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.*;
import javax.imageio.spi.*;
import javax.imageio.stream.*;
import org.springframework.stereotype.Component;
import com.sma.admin.common.BizException;
import com.sma.admin.file.*;

/** Decode raster pixels, discard metadata/animation/trailing payloads, and encode a bounded static PNG. Not a malware scanner. */
@Component
public class PublicImageEncoder {
    public static final int MAX_BYTES=2*1024*1024;
    private final java.util.concurrent.Semaphore decoding=new java.util.concurrent.Semaphore(4);
    public ImagePublication.Image encode(Path path) {
        if(!decoding.tryAcquire()) throw new BizException(com.sma.admin.common.CommonErrorCode.SERVICE_UNAVAILABLE);
        try { return decode(path); } finally { decoding.release(); }
    }
    private ImagePublication.Image decode(Path path) {
        try {
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>MAX_BYTES) throw invalid();
            byte[] bytes;
            try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) { bytes=input.readNBytes(MAX_BYTES+1); }
            if(bytes.length>MAX_BYTES) throw invalid();
            try(var image=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var reader=reader(bytes);
                try {
                    reader.setInput(image,true,true);
                    int width=reader.getWidth(0),height=reader.getHeight(0);
                    if(width<1 || height<1 || width>4096 || height>4096 || (long)width*height>4_000_000) throw invalid();
                    var decoded=reader.read(0);if(decoded==null) throw invalid();
                    var raster=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                    var graphics=raster.createGraphics();try { graphics.drawImage(decoded,0,0,null); } finally { graphics.dispose();decoded.flush(); }
                    var output=new BoundedOutput();var writer=writer();
                    try(var sink=new BoundedImageOutput(output)) {
                        writer.setOutput(sink);writer.write(null,new IIOImage(raster,null,null),writer.getDefaultWriteParam());sink.flush();
                    } finally { writer.dispose();raster.flush(); }
                    byte[] png=output.toByteArray();
                    return new ImagePublication.Image(png,"image/png",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png)),width,height);
                } finally { reader.dispose(); }
            }
        } catch(BizException failure) { throw failure; }
        catch(Exception failure) { throw invalid(); }
    }
    private ImageReader reader(byte[] bytes) throws IOException {
        String type;
        if(bytes.length>=8 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10})) type="png.PNG";
        else if(bytes.length>=3 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255) type="jpeg.JPEG";
        else if(bytes.length>=6 && bytes[0]=='G' && bytes[1]=='I' && bytes[2]=='F' && bytes[3]=='8' && (bytes[4]=='7'||bytes[4]=='9') && bytes[5]=='a') type="gif.GIF";
        else throw invalid();
        String expected="com.sun.imageio.plugins."+type+"ImageReaderSpi";
        var providers=IIORegistry.getDefaultInstance().getServiceProviders(ImageReaderSpi.class,false);
        while(providers.hasNext()) {
            var provider=providers.next();
            // Do not invoke arbitrary canDecodeInput/createReaderInstance before applying the allowlist.
            if(jdkProvider(provider,expected)) return provider.createReaderInstance();
        }
        throw invalid();
    }
    private ImageWriter writer() throws IOException {
        var providers=IIORegistry.getDefaultInstance().getServiceProviders(ImageWriterSpi.class,false);
        while(providers.hasNext()) {
            var provider=providers.next();
            if(jdkProvider(provider,"com.sun.imageio.plugins.png.PNGImageWriterSpi")) return provider.createWriterInstance();
        }
        throw invalid();
    }
    private boolean jdkProvider(Object provider,String name) {
        return name.equals(provider.getClass().getName()) && "java.desktop".equals(provider.getClass().getModule().getName());
    }
    /** Bound ImageIO's seekable cache as well as the final byte stream, without disk cache or global SPIs. */
    private static final class BoundedImageOutput extends MemoryCacheImageOutputStream {
        BoundedImageOutput(OutputStream stream) { super(stream); }
        @Override public void write(int value) throws IOException { limit(1);super.write(value); }
        @Override public void write(byte[] bytes,int offset,int length) throws IOException { limit(length);super.write(bytes,offset,length); }
        @Override public void seek(long position) throws IOException { if(position<0 || position>MAX_BYTES) throw invalid();super.seek(position); }
        private void limit(int length) { if(length<0 || streamPos>MAX_BYTES-length) throw invalid(); }
    }
    private static final class BoundedOutput extends ByteArrayOutputStream {
        @Override public synchronized void write(int value) { limit(1);super.write(value); }
        @Override public synchronized void write(byte[] data,int offset,int length) { limit(length);super.write(data,offset,length); }
        private void limit(int addition) { if(addition<0 || count>MAX_BYTES-addition) throw invalid(); }
    }
    private static BizException invalid() { return new BizException(FileErrorCode.INVALID_IMAGE); }
}
