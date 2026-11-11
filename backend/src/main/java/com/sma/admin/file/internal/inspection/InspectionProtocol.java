package com.sma.admin.file.internal.inspection;

import com.sma.admin.file.FileFormat;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Private worker protocol. A content PASS is only one gate and is never a malware verdict or a READY grant. */
public final class InspectionProtocol {
    public static final String ENGINE="sma-content/1";
    public static final int MESSAGE_BYTES=16384;
    public enum Verdict { PASS, REJECT, ERROR }
    public enum Code { NONE, CONTENT_REJECTED, PROCESSOR_UNAVAILABLE, CAPACITY, IO, DEADLINE }
    public record Request(int schema,FileFormat format,long size,String checksum,long outputBudget){
        public Request{if(schema!=1 || format==null || size<1 || size>20L*1024*1024*1024 || checksum==null || !checksum.matches("[0-9a-f]{64}")
                || outputBudget<0 || outputBudget>256L*1024*1024)throw new IllegalArgumentException("Invalid inspection request");}
    }
    public record Normalized(String name,FileFormat format,long size,String checksum){
        public Normalized{if(!("normalized.png".equals(name) && format==FileFormat.PNG || "normalized.jpg".equals(name) && format==FileFormat.JPEG)
                || size<1 || checksum==null || !checksum.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid normalized object");}
    }
    public record Report(int schema,String engine,Verdict verdict,Code code,FileFormat format,long sourceSize,String sourceChecksum,Normalized normalized){
        public Report{if(schema!=1 || !ENGINE.equals(engine) || verdict==null || code==null || format==null || sourceSize<1 || sourceChecksum==null || !sourceChecksum.matches("[0-9a-f]{64}")
                || (verdict==Verdict.PASS)!=(code==Code.NONE) || normalized!=null && verdict!=Verdict.PASS)throw new IllegalArgumentException("Invalid inspection report");}
    }
    private InspectionProtocol(){}
    public static JsonMapper mapper(){return JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(512).maxNumberLength(32).maxDocumentLength(MESSAGE_BYTES).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();}
    public static Report report(Request request,Verdict verdict,Code code){return new Report(1,ENGINE,verdict,code,request.format(),request.size(),request.checksum(),null);}
    public static Report verify(Request request,byte[] bytes){
        if(bytes==null || bytes.length<1 || bytes.length>MESSAGE_BYTES)throw new IllegalArgumentException("Invalid inspection report length");
        Report report=mapper().readValue(bytes,Report.class);
        boolean image=java.util.Set.of(FileFormat.PNG,FileFormat.JPEG,FileFormat.GIF,FileFormat.WEBP).contains(request.format());
        if(report.format()!=request.format() || report.sourceSize()!=request.size() || !report.sourceChecksum().equals(request.checksum())
                || report.normalized()!=null && report.normalized().size()>request.outputBudget()
                || report.verdict()==Verdict.PASS && image!=(report.normalized()!=null))throw new IllegalArgumentException("Inspection report does not match source");
        return report;
    }
}
