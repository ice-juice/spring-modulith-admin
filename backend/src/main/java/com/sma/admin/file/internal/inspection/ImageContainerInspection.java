package com.sma.admin.file.internal.inspection;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.CRC32;
import com.sma.admin.file.FileFormat;

/** Strict outer framing before decoder use. Rejects trailing bytes and truncated structures; does not replace decoding or AV. */
final class ImageContainerInspection {
    private ImageContainerInspection(){}
    static void inspect(Path path,FileFormat format)throws IOException{
        try(var input=new DataInputStream(new BufferedInputStream(Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)))){
            switch(format){case PNG->png(input);case JPEG->jpeg(input);case GIF->gif(input);case WEBP->webp(input,Files.size(path));default->throw invalid();}
            if(input.read()!=-1)throw invalid();
        }
    }
    private static void png(DataInputStream input)throws IOException{
        if(input.readLong()!=0x89504e470d0a1a0aL)throw invalid();boolean header=false,data=false,endData=false;int chunks=0;
        for(;;){
            if(++chunks>10000)throw invalid();long length=Integer.toUnsignedLong(input.readInt());if(length>ImageContentInspection.MAXIMUM_BYTES)throw invalid();
            byte[] type=input.readNBytes(4);if(type.length!=4)throw invalid();
            for(byte b:type)if(!(b>='A'&&b<='Z'||b>='a'&&b<='z'))throw invalid();
            String name=new String(type,StandardCharsets.US_ASCII);
            if(!header && !name.equals("IHDR") || name.equals("IHDR") && (header || length!=13) || name.equals("IEND") && (length!=0 || !data)
                    || name.equals("acTL") || name.equals("fcTL") || name.equals("fdAT"))throw invalid();
            if((type[0]&32)==0 && !java.util.Set.of("IHDR","PLTE","IDAT","IEND").contains(name))throw invalid();
            if(name.equals("IDAT")){if(endData)throw invalid();data=true;}else if(data)endData=true;
            var crc=new CRC32();crc.update(type);byte[] buffer=new byte[65536];long remaining=length;
            while(remaining>0){int count=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(count<0)throw invalid();crc.update(buffer,0,count);remaining-=count;}
            if(Integer.toUnsignedLong(input.readInt())!=crc.getValue())throw invalid();
            header=true;if(name.equals("IEND"))return;
        }
    }
    private static void jpeg(DataInputStream input)throws IOException{
        if(input.readUnsignedShort()!=0xffd8)throw invalid();boolean scan=false;int markers=0,pending=-1;
        for(;;){
            if(++markers>100000)throw invalid();int marker;
            if(pending>=0){marker=pending;pending=-1;}else{if(input.readUnsignedByte()!=255)throw invalid();do{marker=input.readUnsignedByte();}while(marker==255);}
            if(marker==0xd9){if(!scan)throw invalid();return;}
            if(marker==0 || marker==0xd8 || marker>=0xd0&&marker<=0xd7)throw invalid();
            if(marker==1)continue;
            int length=input.readUnsignedShort();if(length<2)throw invalid();input.skipNBytes(length-2L);
            if(marker==0xda){
                scan=true;
                for(;;){int b=input.readUnsignedByte();if(b!=255)continue;do{b=input.readUnsignedByte();}while(b==255);
                    if(b==0 || b>=0xd0&&b<=0xd7)continue;pending=b;break;}
            }
        }
    }
    private static void gif(DataInputStream input)throws IOException{
        String header=new String(input.readNBytes(6),StandardCharsets.US_ASCII);if(!header.equals("GIF87a")&&!header.equals("GIF89a"))throw invalid();
        input.skipNBytes(4);int flags=input.readUnsignedByte();input.skipNBytes(2);if((flags&128)!=0)input.skipNBytes(3L*(1<<((flags&7)+1)));
        int frames=0,blocks=0;
        for(;;){
            if(++blocks>10000)throw invalid();int type=input.readUnsignedByte();
            if(type==0x3b){if(frames==0)throw invalid();return;}
            if(type==0x21){input.readUnsignedByte();subblocks(input);}
            else if(type==0x2c){
                if(++frames>ImageContentInspection.MAXIMUM_FRAMES)throw invalid();input.skipNBytes(8);flags=input.readUnsignedByte();
                if((flags&128)!=0)input.skipNBytes(3L*(1<<((flags&7)+1)));int codeSize=input.readUnsignedByte();if(codeSize<2 || codeSize>8)throw invalid();subblocks(input);
            }else throw invalid();
        }
    }
    private static void subblocks(DataInputStream input)throws IOException{for(int blocks=0;blocks<150000;blocks++){int length=input.readUnsignedByte();if(length==0)return;input.skipNBytes(length);}throw invalid();}
    private static void webp(DataInputStream input,long size)throws IOException{
        if(input.readInt()!=0x52494646 || Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()))!=size-8 || input.readInt()!=0x57454250)throw invalid();
        long position=12;int images=0,chunks=0;
        while(position<size){
            if(size-position<8 || ++chunks>10000)throw invalid();String type=new String(input.readNBytes(4),StandardCharsets.US_ASCII);
            long length=Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()));position+=8;if(length>size-position)throw invalid();
            if(type.equals("ANIM") || type.equals("ANMF"))throw invalid(); // Animated WebP requires a separately validated decoder contract.
            if(type.equals("VP8 ") || type.equals("VP8L"))images++;
            input.skipNBytes(length);position+=length;if((length&1)!=0){if(input.readUnsignedByte()!=0)throw invalid();position++;}
        }
        if(position!=size || images!=1)throw invalid();
    }
    private static IOException invalid(){return new IOException("Image container rejected");}
}
