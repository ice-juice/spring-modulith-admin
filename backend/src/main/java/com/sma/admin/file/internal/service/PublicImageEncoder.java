package com.sma.admin.file.internal.service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
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
            try(var input=Files.newInputStream(path)) { bytes=input.readNBytes(MAX_BYTES+1); }
            if(bytes.length>MAX_BYTES) throw invalid();
            try(var image=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                if(image==null) throw invalid();var readers=ImageIO.getImageReaders(image);if(!readers.hasNext()) throw invalid();
                var reader=readers.next();
                try {
                    reader.setInput(image,true,true);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    if(!Set.of("png","jpeg","jpg","gif").contains(format)) throw invalid();
                    int width=reader.getWidth(0),height=reader.getHeight(0);
                    if(width<1 || height<1 || width>4096 || height>4096 || (long)width*height>4_000_000) throw invalid();
                    var decoded=reader.read(0);if(decoded==null) throw invalid();
                    var raster=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                    var graphics=raster.createGraphics();try { graphics.drawImage(decoded,0,0,null); } finally { graphics.dispose();decoded.flush(); }
                    var output=new BoundedOutput();try { if(!ImageIO.write(raster,"png",output)) throw invalid(); } finally { raster.flush(); }
                    byte[] png=output.toByteArray();
                    return new ImagePublication.Image(png,"image/png",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png)),width,height);
                } finally { reader.dispose(); }
            }
        } catch(BizException failure) { throw failure; }
        catch(Exception failure) { throw invalid(); }
    }
    private static final class BoundedOutput extends ByteArrayOutputStream {
        @Override public synchronized void write(int value) { limit(1);super.write(value); }
        @Override public synchronized void write(byte[] data,int offset,int length) { limit(length);super.write(data,offset,length); }
        private void limit(int addition) { if(addition<0 || count>MAX_BYTES-addition) throw invalid(); }
    }
    private static BizException invalid() { return new BizException(FileErrorCode.INVALID_IMAGE); }
}
