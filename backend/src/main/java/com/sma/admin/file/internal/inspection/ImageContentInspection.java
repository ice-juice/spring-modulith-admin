package com.sma.admin.file.internal.inspection;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.*;
import com.sma.admin.file.FileFormat;
import static com.sma.admin.file.internal.inspection.InspectionProtocol.*;

/** Worker-only raster decoder and metadata-free static PNG derivative. Original/derivative still require antivirus. */
final class ImageContentInspection {
    static final long MAXIMUM_BYTES=32L*1024*1024,MAXIMUM_PIXELS=16000000,MAXIMUM_TOTAL_PIXELS=32000000,MAXIMUM_OUTPUT=64L*1024*1024;
    static final int MAXIMUM_DIMENSION=8192,MAXIMUM_FRAMES=32;
    private ImageContentInspection(){}
    /** Archive media remains inside its attachment; decode every frame but do not publish a standalone derivative. */
    static void validate(Path source,Request request)throws IOException{inspect(source,null,request);}
    static Normalized inspect(Path source,Path output,Request request)throws IOException{
        if(output!=null && request.outputBudget()<1)throw new Capacity();ImageContainerInspection.inspect(source,request.format());
        ImageReader reader=null;BufferedImage first=null;
        try(var input=new FileImageInputStream(source.toFile())){
            String provider=request.format()==FileFormat.WEBP?"com.twelvemonkeys.imageio.plugins.webp.WebPImageReader":"com.sun.imageio.plugins."+switch(request.format()){
                case PNG->"png.PNGImageReader";case JPEG->"jpeg.JPEGImageReader";case GIF->"gif.GIFImageReader";default->throw new IOException("Raster format unavailable");};
            var candidates=ImageIO.getImageReaders(input);while(candidates.hasNext()){
                var candidate=candidates.next();if(candidate.getClass().getName().equals(provider)){reader=candidate;break;}candidate.dispose();
            }
            if(reader==null)throw new Unavailable();
            final boolean[] warning={false};reader.addIIOReadWarningListener((ignored,message)->warning[0]=true);reader.setInput(input,false,false);
            int frames=reader.getNumImages(true);if(frames<1 || frames>MAXIMUM_FRAMES)throw new Capacity();long total=0;
            for(int i=0;i<frames;i++){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Image inspection interrupted");
                int width=reader.getWidth(i),height=reader.getHeight(i);long pixels=(long)width*height;
                if(width<1 || height<1 || width>MAXIMUM_DIMENSION || height>MAXIMUM_DIMENSION || pixels>MAXIMUM_PIXELS || (total+=pixels)>MAXIMUM_TOTAL_PIXELS)throw new Capacity();
                BufferedImage decoded=reader.read(i);if(decoded==null || decoded.getWidth()!=width || decoded.getHeight()!=height || warning[0])throw new IOException("Image decode rejected");
                if(i==0 && output!=null){
                    first=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                    // Copy pixels to a new raster. Decoder metadata, EXIF/GPS, profiles and comments never enter the writer.
                    int[] row=new int[width];for(int y=0;y<height;y++){decoded.getRGB(0,y,width,1,row,0,width);first.setRGB(0,y,width,1,row,0,width);}
                }
                decoded.flush();
            }
            if(warning[0])throw new IOException("Image warning rejected");
        }finally{if(reader!=null)reader.dispose();}
        if(output==null)return null;
        Path target=output.resolve("normalized.png");long maximum=Math.min(request.outputBudget(),MAXIMUM_OUTPUT);ImageWriter writer=null;
        try(var sink=new LimitedImageOutput(target,maximum)){
            var writers=ImageIO.getImageWritersByFormatName("png");while(writers.hasNext()){
                var candidate=writers.next();if(candidate.getClass().getName().equals("com.sun.imageio.plugins.png.PNGImageWriter")){writer=candidate;break;}candidate.dispose();
            }
            if(writer==null)throw new Unavailable();writer.setOutput(sink);writer.write(null,new IIOImage(first,null,null),writer.getDefaultWriteParam());sink.flush();sink.force();
        }catch(IOException failure){
            // ImageIO writers may wrap a bounded sink's exception. Capacity/IO failure is not a content-malware verdict.
            for(Throwable cause=failure;cause!=null;cause=cause.getCause()){
                if(cause instanceof Capacity capacity)throw capacity;
                if(cause instanceof Unavailable unavailable)throw unavailable;
            }
            throw new OutputFailure();
        }finally{if(writer!=null)writer.dispose();if(first!=null)first.flush();}
        // A partial output is quarantined evidence, never a published object; process parent owns cleanup.
        long size=Files.size(target);if(size<1 || size>maximum)throw new Capacity();
        return new Normalized("normalized.png",FileFormat.PNG,size,checksum(target));
    }
    static String checksum(Path path)throws IOException{
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}
        return HexFormat.of().formatHex(digest.digest());
    }
    static final class Capacity extends IOException {}
    static final class Unavailable extends IOException {}
    static final class OutputFailure extends IOException {}
    /** Seekable bounded output avoids ImageIO's global disk/memory cache and never replaces an existing derivative. */
    private static final class LimitedImageOutput extends ImageOutputStreamImpl {
        private final FileChannel channel;private final long maximum;
        LimitedImageOutput(Path path,long maximum)throws IOException{this.maximum=maximum;channel=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);}
        @Override public int read()throws IOException{byte[] one=new byte[1];return read(one,0,1)<0?-1:one[0]&255;}
        @Override public int read(byte[] bytes,int offset,int count)throws IOException{checkClosed();bitOffset=0;int read=channel.read(ByteBuffer.wrap(bytes,offset,count),streamPos);if(read>0)streamPos+=read;return read;}
        @Override public void write(int value)throws IOException{write(new byte[]{(byte)value},0,1);}
        @Override public void write(byte[] bytes,int offset,int count)throws IOException{
            checkClosed();flushBits();if(count>maximum-streamPos)throw new Capacity();var buffer=ByteBuffer.wrap(bytes,offset,count);
            while(buffer.hasRemaining()){int written=channel.write(buffer,streamPos);streamPos+=written;}
        }
        @Override public void seek(long position)throws IOException{if(position<0 || position>maximum)throw new Capacity();super.seek(position);}
        @Override public long length(){try{return channel.size();}catch(IOException failure){return -1;}}
        void force()throws IOException{channel.force(true);}
        @Override public void close()throws IOException{try{super.close();}finally{channel.close();}}
    }
}
