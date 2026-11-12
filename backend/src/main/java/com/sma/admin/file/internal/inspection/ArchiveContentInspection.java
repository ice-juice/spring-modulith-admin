package com.sma.admin.file.internal.inspection;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import com.sma.admin.common.BizException;
import com.sma.admin.file.FileFormat;
import com.sma.admin.file.internal.service.FileContentProbe;
import com.sma.admin.file.internal.service.FileNameRules;
import static com.sma.admin.file.internal.inspection.InspectionProtocol.*;

/** Worker-only recursive content checks. Generated scratch names never derive from archive paths. */
final class ArchiveContentInspection {
    private static final Set<FileFormat> PACKAGES=Set.of(FileFormat.ZIP,FileFormat.DOCX,FileFormat.XLSX,FileFormat.PPTX);
    private static final Set<FileFormat> IMAGES=Set.of(FileFormat.PNG,FileFormat.JPEG,FileFormat.GIF,FileFormat.WEBP);
    private static final int MAXIMUM_NESTED_DEPTH=2;
    static final class ScratchFailure extends IOException {ScratchFailure(){super("Archive scratch storage unavailable");}}
    private static final class Budget {
        final long maximum;long bytes;int entries;
        Budget(long maximum){this.maximum=Math.min(maximum,ArchiveInspection.MAXIMUM_EXPANDED);}
    }
    private ArchiveContentInspection(){}
    static void inspect(Path source,Path scratch,Request request)throws IOException{
        if(!PACKAGES.contains(request.format()))throw new ArchiveInspection.Rejected();
        if(!Files.isDirectory(scratch,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(scratch))throw new ScratchFailure();
        archive(source,scratch,request.format(),new Budget(request.outputBudget()),0);
    }
    private static void archive(Path source,Path scratch,FileFormat format,Budget budget,int depth)throws IOException{
        if(depth>MAXIMUM_NESTED_DEPTH)throw new ArchiveInspection.Capacity();
        var manifest=ArchiveInspection.inspect(source,budget.maximum-budget.bytes,ArchiveInspection.MAXIMUM_ENTRIES-budget.entries);
        budget.bytes+=manifest.expandedSize();budget.entries+=manifest.entries().size();
        var members=format==FileFormat.ZIP?manifest.entries():OfficePackageInspection.inspect(source,format,manifest).media();
        try(var zip=org.apache.commons.compress.archivers.zip.ZipFile.builder().setPath(source).get()){
            for(var member:members){
                if(member.directory())continue;
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Archive content check interrupted");
                FileFormat type=format(member.name());
                Path temporary;
                try{temporary=Files.createTempFile(scratch,"member-",".blob");}catch(IOException unavailable){throw new ScratchFailure();}
                try{
                    materialize(zip,member,temporary);
                    if(member.size()==0){if(type!=FileFormat.TEXT && type!=FileFormat.CSV)throw new ArchiveInspection.Rejected();continue;}
                    long maximum=PACKAGES.contains(type)?ArchiveInspection.MAXIMUM_BYTES:IMAGES.contains(type)?ImageContentInspection.MAXIMUM_BYTES:type==FileFormat.PDF?PdfContentInspection.MAXIMUM_BYTES:type==FileFormat.MP4?VideoContentInspection.MAXIMUM_BYTES:TextContentInspection.MAXIMUM_BYTES;
                    if(member.size()>maximum)throw new ArchiveInspection.Capacity();
                    FileContentProbe.inspect(Files.newInputStream(temporary,LinkOption.NOFOLLOW_LINKS),type,member.size(),member.checksum(),new FileContentProbe.Limits(maximum,Duration.ofSeconds(60)));
                    if(PACKAGES.contains(type))archive(temporary,scratch,type,budget,depth+1);
                    else if(IMAGES.contains(type))ImageContentInspection.validate(temporary,new Request(1,type,member.size(),member.checksum(),0));
                    else if(type==FileFormat.JSON)TextContentInspection.json(temporary);
                    else if(type==FileFormat.CSV)TextContentInspection.csv(temporary);
                    else if(type==FileFormat.PDF)PdfContentInspection.inspect(temporary);
                    else if(type==FileFormat.MP4)VideoContentInspection.inspect(temporary);
                }finally{
                    try{Files.deleteIfExists(temporary);}catch(IOException unavailable){throw new ScratchFailure();}
                }
            }
        }
    }
    private static void materialize(org.apache.commons.compress.archivers.zip.ZipFile zip,ArchiveInspection.Entry member,Path temporary)throws IOException{
        try(var input=zip.getInputStream(zip.getEntry(member.name()));var output=Files.newOutputStream(temporary,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            long count=0;byte[] bytes=new byte[65536];int read;
            while((read=input.read(bytes,0,(int)Math.min(bytes.length,member.size()-count+1)))!=-1){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Archive extraction interrupted");
                count+=read;if(count>member.size())throw new ArchiveInspection.Rejected();output.write(bytes,0,read);
            }
            if(count!=member.size())throw new ArchiveInspection.Rejected();
        }catch(ArchiveInspection.Rejected | InterruptedIOException rejected){throw rejected;}
         catch(IOException unavailable){throw new ScratchFailure();}
        String checksum;try{checksum=ImageContentInspection.checksum(temporary);}catch(IOException unavailable){throw new ScratchFailure();}
        if(!checksum.equals(member.checksum()))throw new ArchiveInspection.Rejected();
    }
    private static FileFormat format(String path)throws IOException{
        String name=path.substring(path.lastIndexOf('/')+1);String extension=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        FileFormat format=switch(extension){
            case "png"->FileFormat.PNG;case "jpg","jpeg"->FileFormat.JPEG;case "gif"->FileFormat.GIF;case "webp"->FileFormat.WEBP;
            case "txt"->FileFormat.TEXT;case "csv"->FileFormat.CSV;case "json"->FileFormat.JSON;case "zip"->FileFormat.ZIP;
            case "docx"->FileFormat.DOCX;case "xlsx"->FileFormat.XLSX;case "pptx"->FileFormat.PPTX;case "pdf"->FileFormat.PDF;case "mp4"->FileFormat.MP4;
            default->throw new ArchiveInspection.Rejected();
        };
        try{FileNameRules.normalize(name,format);}catch(BizException rejected){throw new ArchiveInspection.Rejected();}return format;
    }
}
