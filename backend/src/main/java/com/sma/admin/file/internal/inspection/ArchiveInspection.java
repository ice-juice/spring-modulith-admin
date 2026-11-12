package com.sma.admin.file.internal.inspection;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.*;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

/** Worker-only, bounded ZIP framing and expanded-byte validation. A manifest is NOT a content or antivirus verdict. */
final class ArchiveInspection {
    static final long MAXIMUM_BYTES=64L*1024*1024,MAXIMUM_ENTRY=32L*1024*1024,MAXIMUM_EXPANDED=256L*1024*1024;
    static final int MAXIMUM_ENTRIES=1000,MAXIMUM_RATIO=100;
    record Entry(String name,boolean directory,long size,long compressedSize,long crc,String checksum){}
    record Manifest(List<Entry> entries,long expandedSize){Manifest{entries=List.copyOf(entries);}}
    static final class Rejected extends IOException {Rejected(){super("Archive structure rejected");}}
    static final class Capacity extends IOException {Capacity(){super("Archive resource capacity exceeded");}}
    private record Directory(long offset,long size,int count){}
    private ArchiveInspection(){}

    static Manifest inspect(Path source)throws IOException{
        return inspect(source,MAXIMUM_EXPANDED,MAXIMUM_ENTRIES);
    }
    static Manifest inspect(Path source,long maximumExpanded,int maximumEntries)throws IOException{
        if(maximumExpanded<0 || maximumExpanded>MAXIMUM_EXPANDED || maximumEntries<0 || maximumEntries>MAXIMUM_ENTRIES)throw new IllegalArgumentException("Invalid remaining archive budget");
        long length=Files.size(source);if(length>MAXIMUM_BYTES)throw new Capacity();if(length<22 || !Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw new Rejected();
        try(var raw=new RandomAccessFile(source.toFile(),"r")){
            var directory=directory(raw);if(directory.count()>maximumEntries)throw new Capacity();
            boundedCentral(raw,directory);
            try(var zip=org.apache.commons.compress.archivers.zip.ZipFile.builder().setPath(source).get()){
            var entries=Collections.list(zip.getEntriesInPhysicalOrder());if(entries.size()!=directory.count())throw new Rejected();
            var names=new HashMap<String,Boolean>();var result=new ArrayList<Entry>();long cursor=0,total=0;
            for(var entry:entries){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Archive check interrupted");
                String name=name(entry.getName());String key=entry.isDirectory()?name.substring(0,name.length()-1):name;
                key=Normalizer.normalize(key,Normalizer.Form.NFC).toLowerCase(Locale.ROOT);if(names.putIfAbsent(key,entry.isDirectory())!=null)throw new Rejected();
                int mode=entry.getUnixMode()&0170000;
                if(entry.isUnixSymlink() || mode!=0 && mode!=(entry.isDirectory()?0040000:0100000) || entry.getDiskNumberStart()!=0
                        || entry.getGeneralPurposeBit().usesEncryption() || (entry.getRawFlag()&~0x80e)!=0 || !Set.of(0,8).contains(entry.getMethod())
                        || !zip.canReadEntryData(entry) || entry.getLocalHeaderOffset()!=cursor || entry.getUnparseableExtraFieldData()!=null)throw new Rejected();
                long size=entry.getSize(),compressed=entry.getCompressedSize();
                if(size<0 || compressed<0 || compressed>MAXIMUM_BYTES || size>MAXIMUM_ENTRY || size>MAXIMUM_RATIO*Math.max(1,compressed)
                        || size>maximumExpanded-total)throw new Capacity();
                if(entry.isDirectory() && size!=0 || entry.getMethod()==0 && compressed!=size)throw new Rejected();
                extras(entry.getLocalFileDataExtra());extras(entry.getCentralDirectoryExtra());
                local(raw,entry,directory.offset());
                Entry measured=measure(zip,entry);total+=measured.size();result.add(measured);
                cursor=entry.getDataOffset()+compressed;
                if((entry.getRawFlag()&8)!=0){
                    raw.seek(cursor);long crc=u32(raw);if(crc==0x08074b50L)crc=u32(raw);
                    if(crc!=entry.getCrc() || u32(raw)!=compressed || u32(raw)!=size)throw new Rejected();cursor=raw.getFilePointer();
                }
                if(cursor>directory.offset())throw new Rejected();
            }
            if(cursor!=directory.offset())throw new Rejected();
            // Reject a file whose name is also an ancestor directory (including case/Unicode aliases).
            for(String path:names.keySet()){int slash=path.indexOf('/');while(slash>=0){if(Boolean.FALSE.equals(names.get(path.substring(0,slash))))throw new Rejected();slash=path.indexOf('/',slash+1);}}
            central(raw,directory,entries);return new Manifest(result,total);
            }
        }
    }
    private static Directory directory(RandomAccessFile raw)throws IOException{
        long length=raw.length(),minimum=Math.max(0,length-65557);
        for(long position=length-22;position>=minimum;position--){
            raw.seek(position);if(u32(raw)!=0x06054b50L)continue;
            if(u16(raw)!=0 || u16(raw)!=0)throw new Rejected();int diskCount=u16(raw),count=u16(raw);long size=u32(raw),offset=u32(raw);int comment=u16(raw);
            if(position+22+comment!=length)continue;
            if(count==65535 || offset==0xffffffffL || size==0xffffffffL)throw new Capacity(); // ZIP64 requires a separate capacity profile.
            if(diskCount!=count || offset+size!=position)throw new Rejected();return new Directory(offset,size,count);
        }
        throw new Rejected();
    }
    private static void local(RandomAccessFile raw,ZipArchiveEntry entry,long directory)throws IOException{
        raw.seek(entry.getLocalHeaderOffset());if(u32(raw)!=0x04034b50L)throw new Rejected();
        if(u16(raw)>20)throw new Rejected();int flags=u16(raw),method=u16(raw);raw.skipBytes(4);long crc=u32(raw),compressed=u32(raw),size=u32(raw);int nameLength=u16(raw),extraLength=u16(raw);
        if(flags!=entry.getRawFlag() || method!=entry.getMethod() || nameLength<1 || nameLength>1024
                || entry.getDataOffset()!=entry.getLocalHeaderOffset()+30+nameLength+extraLength || entry.getDataOffset()+entry.getCompressedSize()>directory)throw new Rejected();
        byte[] name=new byte[nameLength];raw.readFully(name);if(!Arrays.equals(name,entry.getRawName()))throw new Rejected();
        try{
            Charset charset=(flags&0x800)!=0?StandardCharsets.UTF_8:Charset.forName("IBM437");
            String decoded=charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(name)).toString();
            if(!decoded.equals(entry.getName()))throw new Rejected();
        }catch(CharacterCodingException invalid){throw new Rejected();}
        byte[] extra=new byte[extraLength];raw.readFully(extra);extras(extra);
        if((flags&8)==0){if(crc!=entry.getCrc() || size!=entry.getSize() || compressed!=entry.getCompressedSize())throw new Rejected();}
        else if(crc!=0 && crc!=entry.getCrc() || size!=0 && size!=entry.getSize() || compressed!=0 && compressed!=entry.getCompressedSize())throw new Rejected();
    }
    /** Count and bound actual central records before a library can allocate its directory model. */
    private static void boundedCentral(RandomAccessFile raw,Directory directory)throws IOException{
        raw.seek(directory.offset());long end=directory.offset()+directory.size();
        for(int i=0;i<directory.count();i++){
            if(end-raw.getFilePointer()<46 || u32(raw)!=0x02014b50L)throw new Rejected();raw.skipBytes(24);
            int name=u16(raw),extra=u16(raw),comment=u16(raw);raw.skipBytes(12);
            if(name<1 || name>1024 || name+extra+comment>end-raw.getFilePointer())throw new Rejected();raw.seek(raw.getFilePointer()+name+extra+comment);
        }
        if(raw.getFilePointer()!=end)throw new Rejected();
    }
    private static void central(RandomAccessFile raw,Directory directory,List<ZipArchiveEntry> entries)throws IOException{
        var offsets=new HashMap<Long,ZipArchiveEntry>();entries.forEach(e->offsets.put(e.getLocalHeaderOffset(),e));raw.seek(directory.offset());
        for(int i=0;i<directory.count();i++){
            if(u32(raw)!=0x02014b50L)throw new Rejected();raw.skipBytes(4);int flags=u16(raw),method=u16(raw);raw.skipBytes(4);
            long crc=u32(raw),compressed=u32(raw),size=u32(raw);int nameLength=u16(raw),extraLength=u16(raw),commentLength=u16(raw),disk=u16(raw);raw.skipBytes(6);long offset=u32(raw);
            var entry=offsets.remove(offset);if(entry==null || disk!=0 || flags!=entry.getRawFlag() || method!=entry.getMethod() || crc!=entry.getCrc()
                    || compressed!=entry.getCompressedSize() || size!=entry.getSize() || nameLength<1 || nameLength>1024)throw new Rejected();
            byte[] name=new byte[nameLength];raw.readFully(name);if(!Arrays.equals(name,entry.getRawName()))throw new Rejected();
            byte[] extra=new byte[extraLength];raw.readFully(extra);extras(extra);raw.seek(raw.getFilePointer()+commentLength);
            if(raw.getFilePointer()>directory.offset()+directory.size())throw new Rejected();
        }
        if(!offsets.isEmpty() || raw.getFilePointer()!=directory.offset()+directory.size())throw new Rejected();
    }
    private static void extras(byte[] bytes)throws IOException{
        try(var raw=new DataInputStream(new ByteArrayInputStream(bytes))){while(raw.available()>0){
            int type=Short.toUnsignedInt(Short.reverseBytes(raw.readShort())),length=Short.toUnsignedInt(Short.reverseBytes(raw.readShort()));
            // Only non-link timestamp/UID metadata. Unicode aliases, encryption, ZIP64 and vendor extensions are not silently interpreted.
            if(!Set.of(0x5455,0x000a,0x7875).contains(type) || length>raw.available())throw new Rejected();raw.skipNBytes(length);
        }}
    }
    private static Entry measure(org.apache.commons.compress.archivers.zip.ZipFile zip,ZipArchiveEntry entry)throws IOException{
        var crc=new CRC32();MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        long total=0;byte[] inputBytes=new byte[65536],outputBytes=new byte[65536];var inflater=entry.getMethod()==8?new Inflater(true):null;
        try(var raw=zip.getRawInputStream(entry)){
            if(raw==null)throw new Rejected();int read;
            while((read=raw.read(inputBytes))!=-1){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Archive check interrupted");
                if(inflater==null){total=accept(entry,crc,digest,inputBytes,read,total);continue;}
                if(inflater.finished())throw new Rejected();inflater.setInput(inputBytes,0,read);
                while(!inflater.needsInput()){
                    int produced=inflater.inflate(outputBytes);total=accept(entry,crc,digest,outputBytes,produced,total);
                    if(inflater.finished()){if(inflater.getRemaining()!=0)throw new Rejected();break;}
                    if(inflater.needsDictionary() || produced==0 && !inflater.needsInput())throw new Rejected();
                }
            }
            if(inflater!=null && !inflater.finished() || total!=entry.getSize() || crc.getValue()!=entry.getCrc())throw new Rejected();
            return new Entry(entry.getName(),entry.isDirectory(),total,entry.getCompressedSize(),crc.getValue(),HexFormat.of().formatHex(digest.digest()));
        }catch(DataFormatException invalid){throw new Rejected();}finally{if(inflater!=null)inflater.end();}
    }
    private static long accept(ZipArchiveEntry entry,CRC32 crc,MessageDigest digest,byte[] bytes,int count,long total)throws IOException{
        if(count>entry.getSize()-total)throw new Rejected();if(count>MAXIMUM_ENTRY-total)throw new Capacity();crc.update(bytes,0,count);digest.update(bytes,0,count);return total+count;
    }
    private static String name(String value)throws IOException{
        if(value==null || value.isEmpty() || value.length()>240 || value.startsWith("/") || value.contains("\\") || value.contains(":")
                || value.codePoints().anyMatch(c->Character.isISOControl(c) || Character.getType(c)==Character.FORMAT))throw new Rejected();
        String path=value.endsWith("/")?value.substring(0,value.length()-1):value;String[] parts=path.split("/",-1);if(parts.length>16)throw new Capacity();
        for(String part:parts)if(part.isBlank() || part.equals(".") || part.equals("..") || !part.equals(part.strip()) || part.endsWith(".") || part.endsWith(" ")
                || part.matches("(?i)(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?") || part.chars().anyMatch(c->"*?\"<>|".indexOf(c)>=0))throw new Rejected();
        return value;
    }
    private static int u16(RandomAccessFile raw)throws IOException{return Short.toUnsignedInt(Short.reverseBytes(raw.readShort()));}
    private static long u32(RandomAccessFile raw)throws IOException{return Integer.toUnsignedLong(Integer.reverseBytes(raw.readInt()));}
}
