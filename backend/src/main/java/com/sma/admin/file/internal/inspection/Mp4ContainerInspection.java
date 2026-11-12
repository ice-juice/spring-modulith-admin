package com.sma.admin.file.internal.inspection;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Restricted non-fragmented ISO-BMFF envelope. External data references are rejected before native probing. */
final class Mp4ContainerInspection {
    static final class Capacity extends IOException {Capacity(){super("Video container capacity exceeded");}}
    static final class Rejected extends IOException {Rejected(){super("Video container rejected");}}
    private static final Set<String> CONTAINERS=Set.of("moov","trak","mdia","minf","stbl","dinf","edts");
    private static final Set<String> VIDEO=Set.of("avc1","avc3","hvc1","hev1","av01","vp09"),AUDIO=Set.of("mp4a","Opus");
    private record Box(long offset,long content,long end,String type){}
    private static final class Track {int references,descriptions;}
    private final RandomAccessFile input;private int boxes,tracks;
    private Mp4ContainerInspection(RandomAccessFile input){this.input=input;}
    static void inspect(Path source)throws IOException{
        try(var input=new RandomAccessFile(source.toFile(),"r")){new Mp4ContainerInspection(input).root();}
    }
    private Box box(long position,long end)throws IOException{
        if(++boxes>100000)throw new Capacity();if(position<0 || end-position<8)throw new Rejected();input.seek(position);
        long size=Integer.toUnsignedLong(input.readInt());byte[] bytes=new byte[4];input.readFully(bytes);String type=new String(bytes,StandardCharsets.US_ASCII);long header=8;
        if(size==1){if(end-position<16)throw new Rejected();size=input.readLong();header=16;}
        if(size<header || size>end-position)throw new Rejected();return new Box(position,position+header,position+size,type);
    }
    private void root()throws IOException{
        long position=0,end=input.length(),media=0;int movie=0;boolean brand=false;
        while(position<end){
            var box=box(position,end);
            switch(box.type()){
                case "ftyp"->{if(position!=0 || brand || box.end()-box.content()<8 || (box.end()-box.content())%4!=0)throw new Rejected();brand=true;}
                case "moov"->{if(++movie>1 || box.end()-box.content()>16L*1024*1024)throw new Capacity();children(box.content(),box.end(),0,null);}
                case "mdat"->media+=box.end()-box.content();
                case "free","skip","wide"->{} // Padding remains part of the full source scanned by antivirus.
                default->throw new Rejected(); // Fragmented/DRM/playlist containers require their own validated profile.
            }
            position=box.end();
        }
        if(!brand || movie!=1 || media<1 || tracks<1 || tracks>8)throw new Rejected();
    }
    private void children(long start,long end,int depth,Track track)throws IOException{
        if(depth>12)throw new Capacity();long position=start;
        while(position<end){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Video envelope interrupted");var box=box(position,end);
            if(box.type().equals("trak")){
                if(track!=null || ++tracks>8)throw new Capacity();var child=new Track();children(box.content(),box.end(),depth+1,child);
                if(child.references!=1 || child.descriptions!=1)throw new Rejected();
            }else if(CONTAINERS.contains(box.type()))children(box.content(),box.end(),depth+1,track);
            else if(box.type().equals("dref"))references(box,track);
            else if(box.type().equals("stsd"))descriptions(box,track);
            else if(Set.of("mvex","moof","sinf","schi","tenc","pssh").contains(box.type()))throw new Rejected();
            position=box.end();
        }
    }
    private void references(Box box,Track track)throws IOException{
        if(track==null || ++track.references!=1 || box.end()-box.content()<8)throw new Rejected();input.seek(box.content());
        if(input.readInt()!=0 || input.readInt()!=1)throw new Rejected();var entry=box(box.content()+8,box.end());
        if(!entry.type().equals("url ") || entry.end()!=box.end() || entry.end()-entry.content()!=4)throw new Rejected();input.seek(entry.content());
        if(input.readInt()!=1)throw new Rejected(); // Only the self-contained flag. No file/URL/URN strings may follow.
    }
    private void descriptions(Box box,Track track)throws IOException{
        if(track==null || ++track.descriptions!=1 || box.end()-box.content()<8)throw new Rejected();input.seek(box.content());if(input.readInt()!=0)throw new Rejected();
        int count=input.readInt();if(count<1 || count>16)throw new Capacity();long position=box.content()+8;
        for(int i=0;i<count;i++){
            var entry=box(position,box.end());boolean video=VIDEO.contains(entry.type()),audio=AUDIO.contains(entry.type());
            if(!video && !audio || entry.end()-entry.content()<(video?78:28))throw new Rejected();input.seek(entry.content()+6);if(input.readUnsignedShort()!=1)throw new Rejected();
            if(audio && input.readUnsignedShort()!=0)throw new Rejected(); // No QuickTime version-dependent external sample descriptions.
            long child=entry.content()+(video?78:28);
            while(child<entry.end()){
                var config=box(child,entry.end());if(Set.of("sinf","schi","tenc","pssh").contains(config.type()))throw new Rejected();child=config.end();
            }
            position=entry.end();
        }
        if(position!=box.end())throw new Rejected();
    }
}
