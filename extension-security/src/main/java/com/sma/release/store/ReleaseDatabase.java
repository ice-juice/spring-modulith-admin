package com.sma.release.store;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import com.sma.extension.protocol.Checks;
import com.sma.extension.protocol.Hashes;
import com.sma.extension.protocol.ProtocolJson;

/** Low-frequency control transactions serialize on a database row, including replicas. Never used on business calls. */
@Component
public class ReleaseDatabase {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    public ReleaseDatabase(JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc; this.transactions = transactions; this.clock = clock;
    }
    public <T> T transaction(Function<Tx, T> operation) {
        return transactions.execute(status -> {
            long revision = jdbc.queryForObject("SELECT revision FROM release_lock WHERE id=1 FOR UPDATE", Long.class);
            return operation.apply(new Tx(revision));
        });
    }
    public final class Tx {
        private long revision;
        private Tx(long revision) { this.revision = revision; }
        public Instant now() { return clock.instant(); }
        public <T> Optional<T> get(String kind, String id, Class<T> type) {
            var rows = jdbc.query("SELECT body FROM release_document WHERE kind=? AND id=?", (rs, n) -> rs.getString(1), kind, id);
            return rows.stream().findFirst().map(json -> ProtocolJson.read(json.getBytes(StandardCharsets.UTF_8), type));
        }
        public <T> List<T> list(String kind, Class<T> type) {
            return jdbc.query("SELECT body FROM release_document WHERE kind=? ORDER BY id", (rs, n) -> ProtocolJson.read(rs.getString(1).getBytes(StandardCharsets.UTF_8), type), kind);
        }
        public void put(String kind, String id, Object document) {
            Checks.text(kind, 40); Checks.text(id, 160);
            String body = new String(ProtocolJson.write(document), StandardCharsets.UTF_8);
            if (jdbc.update("UPDATE release_document SET body=? WHERE kind=? AND id=?", body, kind, id) == 0)
                jdbc.update("INSERT INTO release_document(kind,id,body) VALUES(?,?,?)", kind, id, body);
        }
        public void delete(String kind, String id) { jdbc.update("DELETE FROM release_document WHERE kind=? AND id=?", kind, id); }
        public void audit(String actor, String action, String resource, String outcome, String detailHash) {
            Checks.text(actor, 100); Checks.text(action, 80); Checks.text(resource, 160); Checks.text(outcome, 80); Checks.hash(detailHash);
            String previous = jdbc.query("SELECT entry_hash FROM release_audit ORDER BY sequence_id DESC LIMIT 1", (rs, n) -> rs.getString(1))
                    .stream().findFirst().orElse("0".repeat(64));
            var entry = new Audit(++revision, now(), actor, action, resource, outcome, detailHash, previous);
            String hash = Hashes.sha256(ProtocolJson.write(entry));
            jdbc.update("INSERT INTO release_audit(sequence_id,occurred_at,actor,action,resource_id,outcome,detail_hash,previous_hash,entry_hash) VALUES(?,?,?,?,?,?,?,?,?)",
                    entry.sequence(), entry.occurredAt().toString(), actor, action, resource, outcome, detailHash, previous, hash);
            jdbc.update("UPDATE release_lock SET revision=? WHERE id=1", revision);
        }
    }
    public record Audit(long sequence, Instant occurredAt, String actor, String action, String resource,
            String outcome, String detailHash, String previousHash) {}
}
