package com.sma.admin.file.internal.inspection;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.apache.commons.compress.archivers.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchiveInspectionTests {
    @TempDir Path directory;
    private record Item(String name,byte[] data,int mode){}
    private Item item(String name,String content){return new Item(name,content.getBytes(StandardCharsets.UTF_8),0);}
    private byte[] zip(boolean stored,Item... items)throws Exception{
        var bytes=new ByteArrayOutputStream();
        try(var out=new ZipArchiveOutputStream(bytes)){
            out.setUseZip64(Zip64Mode.Never);out.setEncoding("UTF-8");
            for(var item:items){
                var entry=new ZipArchiveEntry(item.name);entry.setTime(0);entry.setMethod(stored?0:8);if(item.mode!=0)entry.setUnixMode(item.mode);
                var crc=new CRC32();crc.update(item.data);entry.setCrc(crc.getValue());entry.setSize(item.data.length);
                out.putArchiveEntry(entry);out.write(item.data);out.closeArchiveEntry();
            }
        }return bytes.toByteArray();
    }
    private Path file(byte[] bytes)throws Exception{Path file=directory.resolve(UUID.randomUUID()+".zip");Files.write(file,bytes);return file;}
    private ArchiveInspection.Manifest inspect(byte[] bytes)throws Exception{return ArchiveInspection.inspect(file(bytes));}
    private int signature(byte[] bytes,int sig){for(int i=0;i<=bytes.length-4;i++)if(ByteBuffer.wrap(bytes,i,4).order(ByteOrder.LITTLE_ENDIAN).getInt()==sig)return i;throw new AssertionError("signature missing");}
    private void put16(byte[] bytes,int offset,int value){ByteBuffer.wrap(bytes,offset,2).order(ByteOrder.LITTLE_ENDIAN).putShort((short)value);}
    private void put32(byte[] bytes,int offset,int value){ByteBuffer.wrap(bytes,offset,4).order(ByteOrder.LITTLE_ENDIAN).putInt(value);}
    private int get16(byte[] bytes,int offset){return Short.toUnsignedInt(ByteBuffer.wrap(bytes,offset,2).order(ByteOrder.LITTLE_ENDIAN).getShort());}
    private byte[] rawName(String name)throws Exception{
        byte[] encoded=name.getBytes(StandardCharsets.UTF_8),archive=zip(true,item("x".repeat(encoded.length),"x"));int central=signature(archive,0x02014b50);
        System.arraycopy(encoded,0,archive,30,encoded.length);System.arraycopy(encoded,0,archive,central+46,encoded.length);return archive;
    }
    @Test void storedDeflatedDescriptorAndUtf8EntriesAreFullyMeasuredWithoutExtracting()throws Exception{
        for(boolean stored:List.of(true,false)){
            byte[] data="完整校验内容".getBytes(StandardCharsets.UTF_8);var result=inspect(zip(stored,item("folder/",""),new Item("folder/中文.txt",data,0100644),item("other.json","{}")));
            assertThat(result.entries()).hasSize(3);assertThat(result.expandedSize()).isEqualTo(data.length+2);
            assertThat(result.entries().get(1).checksum()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
        }
        try(var files=Files.list(directory)){assertThat(files.allMatch(p->p.getFileName().toString().endsWith(".zip"))).isTrue();}
        assertThat(inspect(zip(false)).entries()).isEmpty();
    }
    @Test void traversalLinksDevicePathsAliasesAndHierarchyCollisionsAreRejected()throws Exception{
        for(String name:List.of("../bad.txt","/root.txt","a/../../bad","C:/bad","a\\bad","a//b","./bad","a./bad","CON.txt","a/evil\u0000.txt","a/hidden\u200b.txt","a/file:stream")){
            assertThatThrownBy(()->inspect(rawName(name)),"raw path %s",name).isInstanceOf(IOException.class);
        }
        for(int mode:List.of(0120777,0010644,0020644))assertThatThrownBy(()->inspect(zip(true,new Item("link",new byte[]{1},mode)))).isInstanceOf(IOException.class);
        for(Item[] entries:List.of(new Item[]{item("A.txt","x"),item("a.txt","y")},new Item[]{item("a","x"),item("a/b.txt","y")},
                new Item[]{item("é.txt","x"),item("e\u0301.txt","y")},new Item[]{item("a/",""),item("a","x")}))
            assertThatThrownBy(()->inspect(zip(true,entries))).isInstanceOf(IOException.class);
    }
    @Test void encryptionMultiDiskAndUnsupportedExtrasAreRejected()throws Exception{
        byte[] encrypted=zip(true,item("a.txt","payload"));int central=signature(encrypted,0x02014b50);put16(encrypted,6,get16(encrypted,6)|1);put16(encrypted,central+8,get16(encrypted,central+8)|1);
        assertThatThrownBy(()->inspect(encrypted)).isInstanceOf(IOException.class);
        byte[] disks=zip(true,item("a.txt","payload"));put16(disks,signature(disks,0x06054b50)+4,1);assertThatThrownBy(()->inspect(disks)).isInstanceOf(IOException.class);
        byte[] method=zip(true,item("a.txt","payload"));put16(method,8,99);put16(method,signature(method,0x02014b50)+10,99);assertThatThrownBy(()->inspect(method)).isInstanceOf(IOException.class);
        var bytes=new ByteArrayOutputStream();try(var out=new ZipArchiveOutputStream(bytes)){
            out.setUseZip64(Zip64Mode.Never);var entry=new ZipArchiveEntry("a.txt");var extra=new UnrecognizedExtraField();extra.setHeaderId(new ZipShort(0x756e));extra.setLocalFileDataData(new byte[]{1,2});entry.addExtraField(extra);
            out.putArchiveEntry(entry);out.write('x');out.closeArchiveEntry();
        }
        assertThatThrownBy(()->inspect(bytes.toByteArray())).isInstanceOf(IOException.class);
    }
    @Test void centralLocalDisagreementCrcTruncationAndTrailingPayloadCannotPass()throws Exception{
        byte[] differentName=zip(true,item("a.txt","payload"));differentName[30]='b';assertThatThrownBy(()->inspect(differentName)).isInstanceOf(IOException.class);
        byte[] crc=zip(true,item("a.txt","payload"));int data=30+get16(crc,26)+get16(crc,28);crc[data]^=1;assertThatThrownBy(()->inspect(crc)).isInstanceOf(IOException.class);
        byte[] mismatch=zip(true,item("a.txt","payload"));put32(mismatch,18,2);assertThatThrownBy(()->inspect(mismatch)).isInstanceOf(IOException.class);
        byte[] valid=zip(false,item("a.txt","payload"));assertThatThrownBy(()->inspect(Arrays.copyOf(valid,valid.length-1))).isInstanceOf(IOException.class);
        var trailing=new ByteArrayOutputStream();trailing.write(valid);trailing.write("<?php ?>".getBytes(StandardCharsets.US_ASCII));assertThatThrownBy(()->inspect(trailing.toByteArray())).isInstanceOf(IOException.class);
    }
    @Test void compressedBombEntryCountZip64AndDeclaredOversizeFailBeforeUnboundedWork()throws Exception{
        assertThatThrownBy(()->inspect(zip(false,item("bomb.txt","a".repeat(100000))))).isInstanceOf(ArchiveInspection.Capacity.class);
        var entries=new Item[1001];for(int i=0;i<entries.length;i++)entries[i]=item("item"+i+".txt","");
        assertThatThrownBy(()->inspect(zip(true,entries))).isInstanceOf(ArchiveInspection.Capacity.class);
        byte[] zip64=zip(true,item("a.txt","x"));put16(zip64,signature(zip64,0x06054b50)+10,65535);assertThatThrownBy(()->inspect(zip64)).isInstanceOf(ArchiveInspection.Capacity.class);
        byte[] oversized=zip(true,item("a.txt","x"));put32(oversized,signature(oversized,0x02014b50)+24,40*1024*1024);assertThatThrownBy(()->inspect(oversized)).isInstanceOf(ArchiveInspection.Capacity.class);
        byte[] falseCount=zip(true,item("a.txt","x"),item("b.txt","y"));int eocd=signature(falseCount,0x06054b50);put16(falseCount,eocd+8,1);put16(falseCount,eocd+10,1);
        assertThatThrownBy(()->inspect(falseCount)).isInstanceOf(ArchiveInspection.Rejected.class);
    }
    @Test void dataDescriptorAndDeflatePayloadAreBothValidated()throws Exception{
        byte[] descriptor=zip(false,item("a.txt","descriptor payload"));int at=signature(descriptor,0x08074b50);descriptor[at+4]^=1;
        assertThatThrownBy(()->inspect(descriptor)).isInstanceOf(IOException.class);
        byte[] corrupted=zip(false,item("a.txt","deflated payload"));int data=30+get16(corrupted,26)+get16(corrupted,28);corrupted[data]^=0x7f;
        assertThatThrownBy(()->inspect(corrupted)).isInstanceOf(IOException.class);
    }
    @Test void forgedSmallExpandedSizeCannotHideABombAndDeflateTrailingBytesAreRejected()throws Exception{
        byte[] bomb=zip(false,item("a.txt","a".repeat(100000)));int central=signature(bomb,0x02014b50);put32(bomb,central+24,1);
        assertThatThrownBy(()->inspect(bomb)).isInstanceOf(ArchiveInspection.Rejected.class);
        // Add a byte inside the declared compressed span, updating every framing offset/size consistently.
        byte[] original=zip(false,item("a.txt","valid deflate"));int descriptor=signature(original,0x08074b50);
        byte[] extra=new byte[original.length+1];System.arraycopy(original,0,extra,0,descriptor);extra[descriptor]=42;
        System.arraycopy(original,descriptor,extra,descriptor+1,original.length-descriptor);int centralAt=signature(extra,0x02014b50),end=signature(extra,0x06054b50);
        int compressed=ByteBuffer.wrap(extra,centralAt+20,4).order(ByteOrder.LITTLE_ENDIAN).getInt();put32(extra,centralAt+20,compressed+1);put32(extra,descriptor+1+8,compressed+1);
        put32(extra,end+16,centralAt);assertThatThrownBy(()->inspect(extra)).isInstanceOf(ArchiveInspection.Rejected.class);
    }
}
