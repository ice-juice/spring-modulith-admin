package com.sma.admin.file.internal.inspection;

import java.io.*;

/** Private container pipe framing. Acknowledgement only releases output lifetime; it never grants READY. */
final class InspectionFrames {
    static final int ACK=6;
    private InspectionFrames(){}
    static byte[] read(InputStream source)throws IOException{
        var bytes=new ByteArrayOutputStream();
        for(;;){
            int value=source.read();if(value<0)throw new EOFException("Incomplete inspection frame");
            if(value=='\n'){if(bytes.size()==0)throw new IOException("Empty inspection frame");return bytes.toByteArray();}
            if(bytes.size()>=InspectionProtocol.MESSAGE_BYTES)throw new IOException("Inspection frame too large");bytes.write(value);
        }
    }
    static void write(OutputStream output,byte[] bytes)throws IOException{
        if(bytes==null || bytes.length==0 || bytes.length>InspectionProtocol.MESSAGE_BYTES)throw new IOException("Invalid inspection frame");
        for(byte value:bytes)if(value=='\n' || value=='\r')throw new IOException("Multiline inspection frame");
        output.write(bytes);output.write('\n');output.flush();
    }
    static void acknowledge(InputStream source)throws IOException{if(source.read()!=ACK)throw new IOException("Inspection output not acknowledged");}
}
