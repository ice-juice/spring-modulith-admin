package com.sma.admin.file.internal.inspection;

import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** Worker-only native metadata probe. No URL input, implicit protocol or client-controlled command options. */
final class VideoContentInspection {
    static final long MAXIMUM_BYTES=20L*1024*1024*1024;
    static final class Capacity extends IOException {Capacity(){super("Video capacity exceeded");}}
    static final class Unavailable extends IOException {Unavailable(){super("Video processor unavailable");}}
    static final class Rejected extends IOException {Rejected(){super("Video content rejected");}}
    private VideoContentInspection(){}
    static void inspect(Path source)throws IOException{
        if(Files.size(source)>MAXIMUM_BYTES)throw new Capacity();Mp4ContainerInspection.inspect(source);
        Path probe;try{probe=Path.of(System.getProperty("sma.file.worker.ffprobe","/usr/bin/ffprobe"));}catch(InvalidPathException invalid){throw new Unavailable();}
        if(!probe.isAbsolute() || !Files.isRegularFile(probe,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(probe))throw new Unavailable();
        try(var process=new BoundedInspectionProcess(1)){
            var result=process.execute(command(probe,source),source.toAbsolutePath().getParent(),new byte[0],Duration.ofSeconds(30));
            if(result.exitCode()!=0)throw new Rejected();verify(result.output(),Files.size(source));
        }catch(BoundedInspectionProcess.Failure failure){
            if(failure.reason()==BoundedInspectionProcess.Reason.START)throw new Unavailable();
            if(failure.reason()==BoundedInspectionProcess.Reason.DEADLINE)throw new InterruptedIOException("Video probe deadline");
            if(failure.reason()==BoundedInspectionProcess.Reason.OUTPUT_LIMIT)throw new Capacity();throw new Unavailable();
        }
    }
    static List<String> command(Path executable,Path source){
        if(!executable.isAbsolute() || !source.isAbsolute())throw new IllegalArgumentException("Worker paths must be absolute");
        return List.of(executable.toString(),"-v","error","-max_alloc","67108864","-threads","1","-format_whitelist","mov","-protocol_whitelist","file",
                "-enable_drefs","0","-use_absolute_path","0","-max_streams","8","-probesize","33554432","-analyzeduration","5000000","-f","mov",
                "-show_entries","format=format_name,duration,size:stream=index,codec_type,codec_name,width,height,r_frame_rate,avg_frame_rate,duration,channels,sample_rate,nb_frames:stream_disposition=attached_pic",
                "-of","json","-i",source.toString());
    }
    static void verify(byte[] response,long expectedSize)throws IOException{
        try{
            if(response==null || response.length==0 || response.length>InspectionProtocol.MESSAGE_BYTES)throw new Rejected();
            var root=InspectionProtocol.mapper().readTree(response);if(!root.isObject())throw new Rejected();
            if(root.has("error") || root.path("programs").size()>0 || root.path("stream_groups").size()>0)throw new Rejected();
            var format=root.path("format");if(!format.isObject() || !"mov,mp4,m4a,3gp,3g2,mj2".equals(format.path("format_name").asString())
                    || integer(format.path("size"),1,MAXIMUM_BYTES)!=expectedSize)throw new Rejected();
            duration(format.path("duration"));var streams=root.path("streams");if(!streams.isArray() || streams.size()<1 || streams.size()>3)throw new Capacity();
            var indexes=new HashSet<Long>();int video=0,audio=0;
            for(var stream:streams){
                if(!stream.isObject() || !indexes.add(integer(stream.path("index"),0,7)) || integer(stream.path("disposition").path("attached_pic"),0,0)!=0)throw new Rejected();
                String type=stream.path("codec_type").asString(),codec=stream.path("codec_name").asString();
                if(type.equals("video")){
                    if(++video>1 || !Set.of("h264","hevc","av1","vp9").contains(codec))throw new Rejected();
                    long width=integer(stream.path("width"),1,4096),height=integer(stream.path("height"),1,2160);if(width*height>4096L*2160)throw new Capacity();
                    rate(stream.path("r_frame_rate"));rate(stream.path("avg_frame_rate"));if(stream.has("nb_frames"))integer(stream.path("nb_frames"),1,1296000);
                }else if(type.equals("audio")){
                    if(++audio>2 || !Set.of("aac","opus").contains(codec))throw new Rejected();integer(stream.path("channels"),1,8);integer(stream.path("sample_rate"),8000,192000);
                }else throw new Rejected();
                if(stream.has("duration"))duration(stream.path("duration"));
            }
            if(video!=1)throw new Rejected();
        }catch(Rejected | Capacity known){throw known;}catch(RuntimeException invalid){throw new Rejected();}
    }
    private static long integer(JsonNode value,long minimum,long maximum)throws IOException{
        String raw=value.asString();if(raw==null || !raw.matches("[0-9]{1,12}"))throw new Rejected();long number=Long.parseLong(raw);
        if(number<minimum || number>maximum)throw new Capacity();return number;
    }
    private static void duration(JsonNode value)throws IOException{
        String raw=value.asString();if(raw==null || !raw.matches("[0-9]{1,6}(?:\\.[0-9]{1,9})?"))throw new Rejected();var number=new BigDecimal(raw);
        if(number.signum()<=0 || number.compareTo(BigDecimal.valueOf(21600))>0)throw new Capacity();
    }
    private static void rate(JsonNode value)throws IOException{
        String raw=value.asString();if(raw==null || !raw.matches("[0-9]{1,9}/[0-9]{1,9}"))throw new Rejected();var parts=raw.split("/");var numerator=new BigDecimal(parts[0]);var denominator=new BigDecimal(parts[1]);
        if(numerator.signum()<=0 || denominator.signum()<=0 || numerator.compareTo(denominator.multiply(BigDecimal.valueOf(60)))>0)throw new Capacity();
    }
}
