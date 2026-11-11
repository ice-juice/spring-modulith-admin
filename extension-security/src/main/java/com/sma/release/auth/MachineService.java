package com.sma.release.auth;

import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.sma.extension.protocol.*;
import com.sma.release.store.ReleaseDatabase;

@Service
public class MachineService {
    public enum Kind { NODE, WORKER, AUDITOR, CONSOLE }
    public record Machine(String id, Kind kind, String tokenHash, Set<String> environments, boolean enabled, long revision) {
        public Machine(String id,Kind kind,String tokenHash,Set<String> environments,boolean enabled) {this(id,kind,tokenHash,environments,enabled,1);}
        public Machine { Checks.id(id); Checks.hash(tokenHash); environments = Set.copyOf(environments); environments.forEach(Checks::id);
            Checks.require(kind!=null && !environments.isEmpty() && environments.size()<=20 && revision>0,"invalid-machine"); }
    }
    private final CredentialService credentials;
    public MachineService(CredentialService credentials) { this.credentials = credentials; }
    public void provision(ReleaseDatabase.Tx tx, String id, Kind kind, Set<String> environments, String secret) {
        Checks.require(tx.get("machine", id, Machine.class).isEmpty(), "machine-already-exists");
        String hash=credentials.tokenHash(secret);
        Checks.require(tx.list("machine",Machine.class).stream().noneMatch(m -> Hashes.equalHex(m.tokenHash(),hash)),"machine-token-already-used");
        tx.put("machine", id, new Machine(id, kind, hash, environments, true));
        tx.audit("local-bootstrap", "machine-create", id, "success", Hashes.sha256(ProtocolJson.write(environments.stream().sorted().toList())));
    }
    public Machine authenticate(ReleaseDatabase.Tx tx, String token, Kind kind, String environment) {
        String hash = credentials.tokenHash(token);
        var machine = tx.list("machine", Machine.class).stream().filter(m -> m.enabled() && m.kind() == kind && Hashes.equalHex(m.tokenHash(), hash)).findFirst()
                .orElseThrow(() -> new ProtocolException("unauthenticated"));
        Checks.require(environment == null || machine.environments().contains(environment), "forbidden");
        return machine;
    }
}
