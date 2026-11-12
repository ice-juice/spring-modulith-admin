package com.sma.admin.file.internal.inspection;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdfparser.*;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.contentstream.operator.Operator;

/** Restricted PDF content profile in the worker. Not a renderer, CDR implementation or malware scanner. */
final class PdfContentInspection {
    static final long MAXIMUM_BYTES=32L*1024*1024,MAXIMUM_STREAM=16L*1024*1024,MAXIMUM_DECODED=64L*1024*1024;
    private static final Set<String> BLOCKED_KEYS=Set.of("JavaScript","JS","OpenAction","AA","A","Launch","EmbeddedFiles","EF","AF","RichMediaContent","RichMediaSettings",
            "XFA","AcroForm","Collection","SubmitForm","ImportData","GoToR","GoToE","URI","3DD","3DA","OPI","Ref");
    private static final Set<String> BLOCKED_TYPES=Set.of("Action","Filespec","EmbeddedFile");
    private static final Set<String> BLOCKED_SUBTYPES=Set.of("FileAttachment","RichMedia","Movie","Sound","Screen","Widget","3D");
    private static final Set<String> FILTERS=Set.of("FlateDecode","Fl","LZWDecode","LZW","ASCII85Decode","A85","ASCIIHexDecode","AHx","RunLengthDecode","RL","DCTDecode","DCT","CCITTFaxDecode","CCF");
    private static final Set<String> ACTIONS=Set.of("JavaScript","Launch","SubmitForm","ImportData","GoToR","GoToE","URI","Rendition","Sound","Movie");
    static final class Capacity extends IOException {Capacity(){super("PDF resource capacity exceeded");}}
    static final class Unavailable extends IOException {Unavailable(){super("PDF filter unavailable");}}
    private record Work(COSBase value,int depth){}
    private PdfContentInspection(){}
    static void inspect(Path source)throws IOException{
        long size=Files.size(source);if(size>MAXIMUM_BYTES)throw new Capacity();if(size<16 || !Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw rejected();ending(source,size);
        try(var input=new RandomAccessReadBufferedFile(source);var document=new PDFParser(input).parse(false)){
            if(document.isEncrypted() || document.getDocument().getEncryptionDictionary()!=null)throw rejected();
            var cos=document.getDocument();var index=cos.getXrefTable();if(index.size()>10000 || cos.getHighestXRefObjectNumber()>1000000)throw new Capacity();
            var queue=new ArrayDeque<Work>();queue.add(new Work(cos.getTrailer(),0));
            for(var key:index.keySet()){var object=cos.getObjectFromPool(key).getObject();if(object==null)throw rejected();queue.add(new Work(object,0));}
            var seen=Collections.newSetFromMap(new IdentityHashMap<COSBase,Boolean>());int nodes=0;long decoded=0,strings=0;
            while(!queue.isEmpty()){
                interrupted();var item=queue.removeFirst();COSBase value=item.value();if(value==null || !seen.add(value))continue;
                if(++nodes>50000 || item.depth()>64 || queue.size()>50000)throw new Capacity();
                if(value instanceof COSObject ref){var object=ref.getObject();if(object==null)throw rejected();queue.add(new Work(object,item.depth()+1));}
                else if(value instanceof COSDictionary dictionary){
                    if(dictionary.size()>4096)throw new Capacity();
                    for(var entry:dictionary.entrySet()){
                        if(BLOCKED_KEYS.contains(entry.getKey().getName()))throw rejected();queue.add(new Work(entry.getValue(),item.depth()+1));
                    }
                    if(BLOCKED_TYPES.contains(dictionary.getNameAsString(COSName.TYPE,"")) || BLOCKED_SUBTYPES.contains(dictionary.getNameAsString(COSName.SUBTYPE,""))
                            || ACTIONS.contains(dictionary.getNameAsString(COSName.S,"")))throw rejected();
                    if(value instanceof COSStream stream){
                        if(stream.containsKey(COSName.F))throw rejected();filters(stream.getFilters());
                        if("Image".equals(stream.getNameAsString(COSName.SUBTYPE)))pixels(stream.getInt(COSName.WIDTH),stream.getInt(COSName.HEIGHT));
                        long length=0;
                        // PDFBox may allocate while constructing its decoder: the enclosing worker heap and hard lifetime are mandatory.
                        try(var bytes=stream.createInputStream()){
                            byte[] buffer=new byte[65536];int read;while((read=bytes.read(buffer))!=-1){interrupted();length+=read;decoded+=read;if(length>MAXIMUM_STREAM || decoded>MAXIMUM_DECODED)throw new Capacity();}
                        }
                    }
                }else if(value instanceof COSArray array){if(array.size()>10000)throw new Capacity();for(var child:array)queue.add(new Work(child,item.depth()+1));}
                else if(value instanceof COSString string){int length=string.getBytes().length;strings+=length;if(length>1024*1024 || strings>16L*1024*1024)throw new Capacity();}
            }
            int pages=0,tokens=0;
            for(var page:document.getPages()){
                if(++pages>1000)throw new Capacity();var box=page.getMediaBox();
                if(!Float.isFinite(box.getWidth()) || !Float.isFinite(box.getHeight()) || box.getWidth()<=0 || box.getHeight()<=0 || box.getWidth()>14400 || box.getHeight()>14400)throw new Capacity();
                var parser=new PDFStreamParser(page);
                try{
                    Object token;while((token=parser.parseNextToken())!=null){
                        interrupted();if(++tokens>1000000)throw new Capacity();
                        if(token instanceof Operator operator && operator.getImageParameters()!=null){
                            var params=operator.getImageParameters();pixels(params.getInt(COSName.WIDTH,COSName.W,-1),params.getInt(COSName.HEIGHT,COSName.H,-1));
                            filters(params.getDictionaryObject(COSName.FILTER,COSName.F));if(operator.getImageData()==null || operator.getImageData().length>MAXIMUM_STREAM)throw new Capacity();
                        }
                    }
                }finally{parser.close();}
            }
            if(pages==0)throw rejected();
        }catch(InvalidPasswordException encrypted){throw rejected();}
    }
    private static void filters(COSBase value)throws IOException{
        if(value==null || value instanceof COSNull)return;
        if(value instanceof COSName name){if(!FILTERS.contains(name.getName()))throw new Unavailable();}
        else if(value instanceof COSArray array){if(array.size()>4)throw new Capacity();for(var item:array){if(!(item instanceof COSName))throw rejected();filters(item);}}
        else throw rejected();
    }
    private static void pixels(int width,int height)throws IOException{if(width<1 || height<1 || width>8192 || height>8192 || (long)width*height>16000000)throw new Capacity();}
    private static void ending(Path source,long size)throws IOException{
        try(var raw=new RandomAccessFile(source.toFile(),"r")){
            byte[] header=new byte[5];raw.readFully(header);if(!Arrays.equals(header,"%PDF-".getBytes(StandardCharsets.US_ASCII)))throw rejected();
            int length=(int)Math.min(size,1024);byte[] tail=new byte[length];raw.seek(size-length);raw.readFully(tail);int end=length;
            while(end>0 && (tail[end-1]==' ' || tail[end-1]=='\r' || tail[end-1]=='\n' || tail[end-1]=='\t' || tail[end-1]=='\f'))end--;
            if(end<5 || !new String(tail,end-5,5,StandardCharsets.US_ASCII).equals("%%EOF"))throw rejected();
        }
    }
    private static void interrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("PDF check interrupted");}
    private static IOException rejected(){return new IOException("PDF content rejected");}
}
