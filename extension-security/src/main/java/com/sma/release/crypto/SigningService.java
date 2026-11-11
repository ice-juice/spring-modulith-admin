package com.sma.release.crypto;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.sma.extension.protocol.*;
import com.sma.release.ReleaseProperties;
import com.sma.release.store.ReleaseDatabase;

/** Keys are generated only in the separate security service and encrypted with its deployment-supplied KEK. */
@Service
public class SigningService {
    private final SecretVault vault;
    private final ReleaseProperties properties;
    public SigningService(SecretVault vault, ReleaseProperties properties) { this.vault = vault; this.properties = properties; }
    private record StoredKey(String id,String environment,String family, String publicKey, String sealedPrivateKey, Instant notBefore, Instant notAfter, Set<SignedClaims.Purpose> purposes, boolean revoked) {}
    private record ActiveKey(String id) {}
    private record KeyAcknowledgement(String nodeId,String environment,java.util.Map<String,String> fingerprints,Instant at) {
        private KeyAcknowledgement {fingerprints=java.util.Map.copyOf(fingerprints);}
    }
    public record Rotation(String operation,String family,String keyId) {}
    public record PublicTrustKey(String id,String environment, String algorithm, String publicKey, Instant notBefore, Instant notAfter, Set<SignedClaims.Purpose> purposes, boolean revoked) {}
    public void initialize(ReleaseDatabase.Tx tx) {
        var environments=tx.list("machine",com.sma.release.auth.MachineService.Machine.class).stream().flatMap(m -> m.environments().stream()).collect(java.util.stream.Collectors.toSet());
        Checks.require(!environments.isEmpty(),"signing-environment-required");
        for(String environment:environments)for(String family:List.of("artifact","execution")) {
            String pointer=environment+":"+family;
            if(tx.get("active-key",pointer,ActiveKey.class).isPresent())continue;
            String id="key-"+family+"-"+Hashes.sha256(environment.getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0,16);
            create(tx,id,environment,family);tx.put("active-key",pointer,new ActiveKey(id));
        }
    }
    private void create(ReleaseDatabase.Tx tx, String id,String environment,String family) {
        Set<SignedClaims.Purpose> purposes=family.equals("artifact")?Set.of(SignedClaims.Purpose.ARTIFACT):
                Set.of(SignedClaims.Purpose.PREPARE,SignedClaims.Purpose.RELEASE,SignedClaims.Purpose.LEASE,SignedClaims.Purpose.REVOCATIONS,SignedClaims.Purpose.POLICY,SignedClaims.Purpose.WORK_ORDER);
        try {
            var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            byte[] privateBytes = pair.getPrivate().getEncoded();
            try {
                tx.put("key", id, new StoredKey(id,environment,family, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()), vault.seal("signing:"+id, privateBytes), tx.now().minusSeconds(1), tx.now().plus(Duration.ofDays(365)), purposes, false));
            } finally { java.util.Arrays.fill(privateBytes, (byte)0); }
            tx.audit("security-service", "key-create", id, "success", Hashes.sha256(pair.getPublic().getEncoded()));
        } catch (java.security.GeneralSecurityException failure) { throw new ProtocolException("signing-key-unavailable", failure); }
    }
    public List<PublicTrustKey> publicKeys(ReleaseDatabase.Tx tx) {
        return tx.list("key", StoredKey.class).stream().map(k -> new PublicTrustKey(k.id(),k.environment(), "Ed25519", k.publicKey(), k.notBefore(), k.notAfter(), k.purposes(), k.revoked())).toList();
    }
    public SignedEnvelope sign(ReleaseDatabase.Tx tx, SignedClaims.Purpose purpose, String environment, Object body, Instant expires) {
        String family=purpose==SignedClaims.Purpose.ARTIFACT?"artifact":"execution";
        String id=tx.get("active-key",environment+":"+family,ActiveKey.class).orElseThrow(() -> new ProtocolException("signing-environment-not-initialized")).id();
        var key = tx.get("key", id, StoredKey.class).orElseThrow(() -> new ProtocolException("signing-key-not-initialized"));
        Checks.require(!key.revoked() && key.environment().equals(environment) && key.purposes().contains(purpose) && !tx.now().isBefore(key.notBefore())
                && expires.isAfter(tx.now()) && !expires.isAfter(key.notAfter()), "signing-key-unavailable");
        byte[] privateBytes = vault.open("signing:"+id, key.sealedPrivateKey());
        try {
            var privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            return DocumentSignatures.sign(new SignedClaims(1, purpose, id, properties.issuer(), properties.application(), environment,
                    UUID.randomUUID().toString(), tx.now(), expires, ProtocolJson.write(body)), privateKey);
        } catch (java.security.GeneralSecurityException failure) { throw new ProtocolException("signing-failed", failure); }
        finally { java.util.Arrays.fill(privateBytes, (byte)0); }
    }
    public String rotate(ReleaseDatabase.Tx tx,String environment,Rotation request) {
        Checks.id(environment);Checks.id(request.keyId());Checks.require(request.family()!=null && Set.of("artifact","execution").contains(request.family()),"invalid-key-family");
        Checks.require(request.operation()!=null && Set.of("stage","activate","retire").contains(request.operation()),"invalid-key-operation");
        String pointer=environment+":"+request.family();var active=tx.get("active-key",pointer,ActiveKey.class).orElseThrow(() -> new ProtocolException("signing-environment-not-initialized"));
        if(request.operation().equals("stage")) {
            Checks.require(tx.get("key",request.keyId(),StoredKey.class).isEmpty(),"key-id-already-used");create(tx,request.keyId(),environment,request.family());return request.keyId();
        }
        var key=tx.get("key",request.keyId(),StoredKey.class).orElseThrow(() -> new ProtocolException("key-not-found"));
        Checks.require(key.environment().equals(environment) && key.family().equals(request.family()) && !key.revoked(),"key-context-mismatch");
        if(request.operation().equals("activate")) {
            String expected=Hashes.sha256(Base64.getDecoder().decode(key.publicKey()));
            var nodes=tx.list("machine",com.sma.release.auth.MachineService.Machine.class).stream().filter(m -> m.enabled() && m.kind()==com.sma.release.auth.MachineService.Kind.NODE && m.environments().contains(environment)).toList();
            for(var node:nodes) {
                var ack=tx.get("key-ack",environment+":"+node.id(),KeyAcknowledgement.class).orElseThrow(() -> new ProtocolException("key-not-installed-on-all-nodes"));
                Checks.require(tx.now().isBefore(ack.at().plusSeconds(900)) && expected.equals(ack.fingerprints().get(key.id())),"key-not-installed-on-all-nodes");
            }
            Checks.require(!nodes.isEmpty(),"target-nodes-required");tx.put("active-key",pointer,new ActiveKey(key.id()));return key.id();
        }
        if(request.operation().equals("retire")) {
            Checks.require(!active.id().equals(key.id()),"active-key-cannot-be-retired");
            for(var node:tx.list("machine",com.sma.release.auth.MachineService.Machine.class)) {
                if(!node.enabled() || node.kind()!=com.sma.release.auth.MachineService.Kind.NODE || !node.environments().contains(environment))continue;
                var ack=tx.get("key-ack",environment+":"+node.id(),KeyAcknowledgement.class).orElseThrow(() -> new ProtocolException("old-key-still-installed"));
                Checks.require(tx.now().isBefore(ack.at().plusSeconds(900)) && !ack.fingerprints().containsKey(key.id()),"old-key-still-installed");
            }
            tx.put("key",key.id(),new StoredKey(key.id(),key.environment(),key.family(),key.publicKey(),key.sealedPrivateKey(),key.notBefore(),key.notAfter(),key.purposes(),true));return key.id();
        }
        throw new ProtocolException("invalid-key-operation");
    }
    public void acknowledge(ReleaseDatabase.Tx tx,String nodeId,String environment,java.util.Map<String,String> fingerprints) {
        Checks.require(fingerprints.size()<=100,"too-many-trust-keys");
        for(var entry:fingerprints.entrySet()) {
            Checks.id(entry.getKey());Checks.hash(entry.getValue());
            var key=tx.get("key",entry.getKey(),StoredKey.class).orElseThrow(() -> new ProtocolException("unknown-trust-key"));
            Checks.require(!key.revoked() && key.environment().equals(environment) && Hashes.sha256(Base64.getDecoder().decode(key.publicKey())).equals(entry.getValue()),"trust-key-fingerprint-mismatch");
        }
        tx.put("key-ack",environment+":"+nodeId,new KeyAcknowledgement(nodeId,environment,fingerprints,tx.now()));
    }
}
