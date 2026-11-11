package com.sma.admin.file.internal.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import com.sma.admin.file.FileFormat;

/** Bounded preliminary classification only. No image/document decoding, malware verdict or publication authority.
 * A ZIP signature does not establish an Office format. Text has no universal magic number.
 * The caller must also bound blocking I/O (this loop's deadline cannot interrupt an arbitrary InputStream.read).
 */
public final class FileContentProbe {
    public enum Signature { PNG, JPEG, GIF, WEBP, ISO_BMFF, PDF, ZIP, UTF8_TEXT }
    public enum Reason { SIZE, CHECKSUM, TYPE, UTF8, CONTROL_CHARACTER, EXECUTABLE_PREFIX, DEADLINE }
    public static final class Rejected extends IOException {
        private final Reason reason;
        public Rejected(Reason reason){super("File content preflight rejected: "+reason);this.reason=reason;}
        public Reason reason(){return reason;}
    }
    public record Limits(long maximumBytes,Duration deadline){
        public Limits{if(maximumBytes<1 || maximumBytes>20L*1024*1024*1024 || deadline==null || deadline.isZero() || deadline.isNegative()
                || deadline.compareTo(Duration.ofMinutes(30))>0)throw new IllegalArgumentException("Invalid file preflight limits");}
    }
    /** Never interpret a Candidate as clean, decoded, safe or READY. */
    public record Candidate(FileFormat claimedFormat,Signature signature,long size,String checksum){}
    private static final int BUFFER=65536;
    private static final Set<String> MP4_BRANDS=Set.of("isom","iso2","iso3","iso4","iso5","iso6","iso7","iso8","iso9","mp41","mp42","avc1","M4V ");
    private FileContentProbe(){}

    /** Closes input on success and rejection. Expected digest refers to the entire immutable frozen source. */
    public static Candidate inspect(InputStream input,FileFormat claimed,long expectedSize,String expectedChecksum,Limits limits)throws IOException{
        Objects.requireNonNull(input);Objects.requireNonNull(limits);
        try(input){
            if(claimed==null || expectedSize<1 || expectedSize>limits.maximumBytes() || expectedChecksum==null || !expectedChecksum.matches("[0-9a-f]{64}"))throw new Rejected(Reason.SIZE);
            var digest=digest();byte[] buffer=new byte[BUFFER],prefix=new byte[64];int prefixSize=0;long count=0,start=System.nanoTime();
            var text=Set.of(FileFormat.TEXT,FileFormat.CSV,FileFormat.JSON).contains(claimed)?new Utf8():null;
            for(;;){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("File preflight interrupted");
                if(System.nanoTime()-start>=limits.deadline().toNanos())throw new Rejected(Reason.DEADLINE);
                int read=input.read(buffer,0,(int)Math.min(BUFFER,expectedSize-count+1));
                if(System.nanoTime()-start>=limits.deadline().toNanos())throw new Rejected(Reason.DEADLINE);
                if(read<0)break;if(read==0)continue;count+=read;if(count>expectedSize)throw new Rejected(Reason.SIZE);
                int copy=Math.min(read,prefix.length-prefixSize);System.arraycopy(buffer,0,prefix,prefixSize,copy);prefixSize+=copy;
                digest.update(buffer,0,read);if(text!=null)for(int i=0;i<read;i++)text.accept(buffer[i]&255);
            }
            if(count!=expectedSize)throw new Rejected(Reason.SIZE);if(text!=null)text.finish();
            String checksum=HexFormat.of().formatHex(digest.digest());if(!checksum.equals(expectedChecksum))throw new Rejected(Reason.CHECKSUM);
            byte[] header=Arrays.copyOf(prefix,prefixSize);if(executable(header))throw new Rejected(Reason.EXECUTABLE_PREFIX);
            Signature signature=signature(header,expectedSize);
            boolean matches=switch(claimed){
                case PNG->signature==Signature.PNG;case JPEG->signature==Signature.JPEG;case GIF->signature==Signature.GIF;case WEBP->signature==Signature.WEBP;
                case MP4->signature==Signature.ISO_BMFF;case PDF->signature==Signature.PDF;case DOCX,XLSX,PPTX,ZIP->signature==Signature.ZIP;
                case TEXT,CSV,JSON->signature==Signature.UTF8_TEXT;
            };
            if(!matches)throw new Rejected(Reason.TYPE);return new Candidate(claimed,signature,count,checksum);
        }
    }
    private static Signature signature(byte[] p,long size)throws Rejected{
        if(starts(p,0,137,80,78,71,13,10,26,10))return Signature.PNG;
        if(starts(p,0,255,216,255))return Signature.JPEG;
        if(ascii(p,0,"GIF87a") || ascii(p,0,"GIF89a"))return Signature.GIF;
        if(ascii(p,0,"RIFF") && ascii(p,8,"WEBP")){
            if(p.length<16 || uint(p,4,true)!=size-8 || !(ascii(p,12,"VP8 ") || ascii(p,12,"VP8L") || ascii(p,12,"VP8X")))throw new Rejected(Reason.TYPE);
            return Signature.WEBP;
        }
        if(ascii(p,4,"ftyp")){
            if(p.length<16 || uint(p,0,false)<16 || uint(p,0,false)>size || uint(p,0,false)%4!=0
                    || !MP4_BRANDS.contains(new String(p,8,4,StandardCharsets.US_ASCII)))throw new Rejected(Reason.TYPE);
            return Signature.ISO_BMFF;
        }
        if(ascii(p,0,"%PDF-")){
            if(p.length<9 || !(p[5]=='1' && p[6]=='.' && p[7]>='0' && p[7]<='7' || ascii(p,5,"2.0")) || p[8]!='\r' && p[8]!='\n')throw new Rejected(Reason.TYPE);
            return Signature.PDF;
        }
        if(starts(p,0,80,75,3,4) || starts(p,0,80,75,5,6))return Signature.ZIP;
        return Signature.UTF8_TEXT; // Only selectable after full strict UTF-8 validation for a declared text type.
    }
    private static boolean executable(byte[] p){
        if(starts(p,0,77,90) || starts(p,0,127,69,76,70) || starts(p,0,202,254,186,190)
                || starts(p,0,254,237,250,206) || starts(p,0,254,237,250,207) || starts(p,0,206,250,237,254) || starts(p,0,207,250,237,254))return true;
        int offset=starts(p,0,239,187,191)?3:0;while(offset<p.length && (p[offset]==' ' || p[offset]=='\t' || p[offset]=='\r' || p[offset]=='\n'))offset++;
        String prefix=new String(p,offset,p.length-offset,StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        return prefix.startsWith("#!") || prefix.startsWith("<?php") || prefix.startsWith("<?=") || prefix.startsWith("<%")
                || prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.startsWith("<svg") || prefix.startsWith("<script");
    }
    private static long uint(byte[] p,int offset,boolean little){long result=0;for(int i=0;i<4;i++)result=(result<<8)|(p[offset+(little?3-i:i)]&255);return result;}
    private static boolean ascii(byte[] p,int offset,String value){if(p.length<offset+value.length())return false;for(int i=0;i<value.length();i++)if((p[offset+i]&255)!=value.charAt(i))return false;return true;}
    private static boolean starts(byte[] p,int offset,int... values){if(p.length<offset+values.length)return false;for(int i=0;i<values.length;i++)if((p[offset+i]&255)!=values[i])return false;return true;}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    /** Incremental Unicode scalar decoder, fixed state across arbitrary byte boundaries. */
    private static final class Utf8 {
        private int remaining,value,minimum;
        void accept(int b)throws Rejected{
            if(remaining>0){
                if((b&0xc0)!=0x80)throw new Rejected(Reason.UTF8);value=(value<<6)|(b&0x3f);
                if(--remaining==0){if(value<minimum || value>0x10ffff || value>=0xd800 && value<=0xdfff)throw new Rejected(Reason.UTF8);scalar(value);}return;
            }
            if(b<0x80){scalar(b);return;}
            if(b>=0xc2 && b<=0xdf){remaining=1;value=b&0x1f;minimum=0x80;}
            else if(b>=0xe0 && b<=0xef){remaining=2;value=b&0x0f;minimum=0x800;}
            else if(b>=0xf0 && b<=0xf4){remaining=3;value=b&7;minimum=0x10000;}
            else throw new Rejected(Reason.UTF8);
        }
        private void scalar(int value)throws Rejected{if(Character.isISOControl(value) && value!='\t' && value!='\r' && value!='\n')throw new Rejected(Reason.CONTROL_CHARACTER);}
        void finish()throws Rejected{if(remaining!=0)throw new Rejected(Reason.UTF8);}
    }
}
