package com.sma.admin.file.internal.inspection;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Pattern;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;

/** Invoked only by the isolated worker. Text preflight has already checked the complete UTF-8 stream. */
final class TextContentInspection {
    static final long MAXIMUM_BYTES=16L*1024*1024;
    private static final Pattern NUMBER=Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]{1,3})?");
    private TextContentInspection(){}
    static void json(Path path)throws IOException{
        var factory=JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder().maxDocumentLength(MAXIMUM_BYTES).maxNestingDepth(64)
                .maxStringLength(262144).maxNumberLength(128).build()).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS);var parser=factory.createParser(input)){
            JsonToken token=parser.nextToken();if(token==null)throw rejected();int depth=0,tokens=0;
            do{
                if(++tokens>1000000)throw rejected();
                if(token==JsonToken.START_OBJECT || token==JsonToken.START_ARRAY)depth++;
                else if(token==JsonToken.END_OBJECT || token==JsonToken.END_ARRAY)depth--;
                if(token==JsonToken.VALUE_STRING)parser.getString(); // Force lazy string decoding and its size constraint.
                if(depth==0){if(parser.nextToken()!=null)throw rejected();return;}
            }while((token=parser.nextToken())!=null);
            throw rejected();
        }
    }
    static void csv(Path path)throws IOException{
        try(var input=new PushbackReader(Files.newBufferedReader(path,StandardCharsets.UTF_8),1)){
            var field=new StringBuilder();boolean quoted=false,closed=false,started=false;int columns=1,rows=1,ch;long characters=0;
            while((ch=input.read())!=-1){
                if(++characters>MAXIMUM_BYTES || rows>1000000 || columns>4096)throw rejected();
                if(characters==1 && ch==0xfeff)continue;
                if(quoted){
                    if(ch=='"'){
                        int next=input.read();if(next=='"'){append(field,'"');characters++;}else{quoted=false;closed=true;if(next!=-1)input.unread(next);}
                    }else append(field,(char)ch);
                    continue;
                }
                if(ch==',' || ch=='\r' || ch=='\n'){
                    safeField(field);field.setLength(0);closed=false;started=false;
                    if(ch==',')columns++;else{columns=1;rows++;if(ch=='\r'){int next=input.read();if(next!='\n' && next!=-1)input.unread(next);else if(next=='\n')characters++;}}
                }else if(ch=='"'){
                    if(started || closed)throw rejected();quoted=true;started=true;
                }else{if(closed)throw rejected();started=true;append(field,(char)ch);}
            }
            if(quoted || columns>4096 || rows>1000000)throw rejected();safeField(field);
        }
    }
    private static void append(StringBuilder field,char ch)throws IOException{if(field.length()>=65536)throw rejected();field.append(ch);}
    private static void safeField(StringBuilder field)throws IOException{
        int start=0;while(start<field.length() && (Character.isWhitespace(field.charAt(start)) || Character.isSpaceChar(field.charAt(start))
                || Character.getType(field.charAt(start))==Character.FORMAT))start++;
        if(start==field.length())return;char first=field.charAt(start);
        if(first=='=' || first=='@')throw rejected();
        if(first=='+' || first=='-'){String value=field.substring(start).stripTrailing();if(value.length()>128 || !NUMBER.matcher(value).matches())throw rejected();}
    }
    private static IOException rejected(){return new IOException("Text structure rejected");}
}
