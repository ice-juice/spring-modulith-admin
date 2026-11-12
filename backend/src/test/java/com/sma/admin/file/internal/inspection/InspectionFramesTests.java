package com.sma.admin.file.internal.inspection;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InspectionFramesTests {
    @Test void framedRequestDoesNotConsumeAnEarlyAcknowledgement()throws Exception{
        var input=new ByteArrayInputStream(new byte[]{'{','}','\n',6});assertThat(InspectionFrames.read(input)).containsExactly('{','}');
        InspectionFrames.acknowledge(input);assertThat(input.read()).isEqualTo(-1);
    }
    @Test void incompleteEmptyOversizedAndWrongAcknowledgementAreRejected()throws Exception{
        for(byte[] invalid:new byte[][]{new byte[0],new byte[]{'{'},new byte[]{'\n'},("a".repeat(16385)+"\n").getBytes(StandardCharsets.US_ASCII)})
            assertThatThrownBy(()->InspectionFrames.read(new ByteArrayInputStream(invalid))).isInstanceOf(IOException.class);
        byte[] maximum=("a".repeat(16384)+"\n").getBytes(StandardCharsets.US_ASCII);assertThat(InspectionFrames.read(new ByteArrayInputStream(maximum))).hasSize(16384);
        for(byte[] invalid:new byte[][]{new byte[0],new byte[]{0},new byte[]{'O','K'}})
            assertThatThrownBy(()->InspectionFrames.acknowledge(new ByteArrayInputStream(invalid))).isInstanceOf(IOException.class);
    }
    @Test void reportFrameIsSingleBoundedLineWithNoUserLogPrefix()throws Exception{
        var output=new ByteArrayOutputStream();InspectionFrames.write(output,new byte[]{'{','}'});assertThat(output.toByteArray()).containsExactly('{','}','\n');
        for(byte[] invalid:new byte[][]{new byte[0],new byte[16385],new byte[]{'{','\n','}'},new byte[]{'{','\r','}'}})
            assertThatThrownBy(()->InspectionFrames.write(new ByteArrayOutputStream(),invalid)).isInstanceOf(IOException.class);
    }
}
