package com.sma.worker;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.sma.extension.protocol.*;

class AuditMirrorTests {
    @TempDir Path root;
    @Test void independentCopyAnchorsPriorHistoryRejectsRewritesAndRecoversAfterPartialBatch()throws Exception {
        var initial=AuditMirror.verify(root);var first=entry(1,initial.hash());var second=entry(2,first.entryHash());
        var one=AuditMirror.append(root,initial,new AuditStream.Batch(0,initial.hash(),List.of(first),2));assertThat(one.sequence()).isEqualTo(1);
        assertThat(AuditMirror.verify(root)).isEqualTo(one);
        var two=AuditMirror.append(root,one,new AuditStream.Batch(1,one.hash(),List.of(second),2));assertThat(AuditMirror.verify(root)).isEqualTo(two);
        assertThatThrownBy(() -> AuditMirror.append(root,two,new AuditStream.Batch(2,"b".repeat(64),List.of(),2))).hasMessage("audit-anchor-mismatch");
        var changed=new AuditStream.Hashed(new AuditStream.Entry(1,Instant.EPOCH,"attacker","rewrite","artifact","ok","a".repeat(64),initial.hash()),first.entryHash());
        Files.write(root.resolve("00000000000000000001.json"),ProtocolJson.write(changed));assertThatThrownBy(() -> AuditMirror.verify(root)).hasMessage("audit-chain-mismatch");
    }
    private AuditStream.Hashed entry(long sequence,String previous){var value=new AuditStream.Entry(sequence,Instant.EPOCH,"operator","publish","artifact","ok","a".repeat(64),previous);return new AuditStream.Hashed(value,Hashes.sha256(ProtocolJson.write(value)));}
}
