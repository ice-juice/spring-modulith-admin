package com.sma.admin.common.internal.cache;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** One bounded hash per tenant/region: eviction cannot detach quota metadata from its data. */
final class RedisCacheStore {
    record Value(byte[] payload, boolean absent) {
        Value { payload = payload.clone(); }
        @Override public byte[] payload() { return payload.clone(); }
    }
    record Stats(long size, long bytes) {}
    private static final String HELPERS = """
            local function nowMillis()
                local t = redis.call('TIME')
                return tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            end
            local function drop(field, value)
                if redis.call('HDEL', KEYS[1], field) == 1 then
                    local count = math.max(0, tonumber(redis.call('HGET', KEYS[1], '_count') or '0') - 1)
                    local bytes = math.max(0, tonumber(redis.call('HGET', KEYS[1], '_bytes') or '0') - #field - #value - 96)
                    redis.call('HSET', KEYS[1], '_count', count, '_bytes', bytes)
                end
            end
            local function deadline(value)
                local split = string.find(value, ':', 1, true)
                if not split then return nil end
                return tonumber(string.sub(value, 1, split - 1)), split
            end
            local function sweep(now, full)
                local cursor = full and '0' or (redis.call('HGET', KEYS[1], '_cursor') or '0')
                repeat
                    local page = redis.call('HSCAN', KEYS[1], cursor, 'MATCH', 'd:*', 'COUNT', 32)
                    cursor = page[1]
                    for i = 1, #page[2], 2 do
                        local field, value = page[2][i], page[2][i + 1]
                        local untilTime = deadline(value)
                        if not untilTime or untilTime <= now then drop(field, value) end
                    end
                until not full or cursor == '0'
                redis.call('HSET', KEYS[1], '_cursor', cursor)
            end
            """;
    private static final DefaultRedisScript<String> SYNC = new DefaultRedisScript<>("""
            local revision = redis.call('HGET', KEYS[1], '_revision')
            local generation = redis.call('HGET', KEYS[1], '_generation')
            local validRevision = revision and #revision == 19 and string.match(revision, '^%d+$')
            if ARGV[5] == '1' and validRevision and revision > ARGV[1] then return '' end
            local count = tonumber(redis.call('HGET', KEYS[1], '_count') or '')
            local bytes = tonumber(redis.call('HGET', KEYS[1], '_bytes') or '')
            local valid = validRevision and generation and #generation == 36 and string.match(generation, '^[a-f0-9%-]+$')
                and count and count >= 0 and bytes and bytes >= 0
            if ARGV[4] == '1' or not valid or revision < ARGV[1] then
                local nextRevision = ARGV[1]
                if validRevision and revision > nextRevision then nextRevision = revision end
                redis.call('UNLINK', KEYS[1])
                redis.call('HSET', KEYS[1], '_revision', nextRevision, '_generation', ARGV[2],
                    '_count', '0', '_bytes', '0', '_cursor', '0')
                generation = ARGV[2]
            end
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return generation
            """, String.class);
    private static final DefaultRedisScript<String> READ = new DefaultRedisScript<>(HELPERS + """
            if redis.call('HGET', KEYS[1], '_generation') ~= ARGV[1] then return nil end
            local value = redis.call('HGET', KEYS[1], ARGV[2])
            if not value then return nil end
            local untilTime, split = deadline(value)
            if not untilTime or untilTime <= nowMillis() then drop(ARGV[2], value); return nil end
            return string.sub(value, split + 1)
            """, String.class);
    private static final DefaultRedisScript<Long> WRITE = new DefaultRedisScript<>(HELPERS + """
            if redis.call('HGET', KEYS[1], '_generation') ~= ARGV[1] then return 0 end
            local now = nowMillis()
            sweep(now, false)
            local previous = redis.call('HGET', KEYS[1], ARGV[2])
            local oldWeight = previous and (#ARGV[2] + #previous + 96) or 0
            local value = tostring(now + tonumber(ARGV[4])) .. ':' .. ARGV[3]
            local weight = #ARGV[2] + #value + 96
            local count = tonumber(redis.call('HGET', KEYS[1], '_count') or '0')
            local bytes = tonumber(redis.call('HGET', KEYS[1], '_bytes') or '0')
            local added = previous and 0 or 1
            if count + added > tonumber(ARGV[5]) or bytes - oldWeight + weight > tonumber(ARGV[6]) then return -1 end
            redis.call('HSET', KEYS[1], ARGV[2], value, '_count', count + added, '_bytes', bytes - oldWeight + weight)
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> EVICT = new DefaultRedisScript<>(HELPERS + """
            if redis.call('HGET', KEYS[1], '_generation') ~= ARGV[1] then return 0 end
            local value = redis.call('HGET', KEYS[1], ARGV[2])
            if value then drop(ARGV[2], value) end
            return 1
            """, Long.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> STATS = new DefaultRedisScript<>(HELPERS + """
            if redis.call('EXISTS', KEYS[1]) == 0 then return {0, 0} end
            sweep(nowMillis(), true)
            return {tonumber(redis.call('HGET', KEYS[1], '_count') or '0'), tonumber(redis.call('HGET', KEYS[1], '_bytes') or '0')}
            """, List.class);

    private final StringRedisTemplate redis;
    private final String namespace;
    private static final Duration IDLE_TTL = Duration.ofDays(1).plusMinutes(1);

    RedisCacheStore(StringRedisTemplate redis, String namespace) {
        if (namespace == null || !namespace.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid cache namespace");
        this.redis = redis; this.namespace = namespace;
    }

    String generation(String region, long tenant) { return synchronize(region, tenant, 0); }

    String synchronize(String region, long tenant, long revision) {
        if (revision < 0) throw new IllegalArgumentException("Invalid cache revision");
        return redis.execute(SYNC, List.of(epochKey(region, tenant)), String.format(java.util.Locale.ROOT, "%019d", revision),
                UUID.randomUUID().toString(), Long.toString(IDLE_TTL.toMillis()), "0");
    }

    Value read(CacheLookup lookup, String generation) {
        String wire = redis.execute(READ, List.of(epochKey(lookup.descriptor().id(), lookup.tenant())), generation, dataKey(lookup, generation));
        if (wire == null) return null;
        if (wire.equals("N")) return new Value(new byte[0], true);
        if (!wire.startsWith("V")) throw new IllegalArgumentException("Invalid cache envelope");
        return new Value(Base64.getDecoder().decode(wire.substring(1)), false);
    }

    /** Old observers must not borrow a newer generation and refill it using an obsolete runtime policy. */
    String acquireGeneration(String region,long tenant,long revision) {
        if(revision<0)throw new IllegalArgumentException("Invalid cache revision");
        String result=redis.execute(SYNC,List.of(epochKey(region,tenant)),String.format(java.util.Locale.ROOT,"%019d",revision),
                UUID.randomUUID().toString(),Long.toString(IDLE_TTL.toMillis()),"0","1");
        return result==null||result.isEmpty()?null:result;
    }

    boolean write(CacheLookup lookup, String generation, Value value, Duration ttl) {
        return write(lookup, generation, value, ttl, lookup.descriptor().policy().maxEntries(), lookup.descriptor().policy().maxBytes());
    }

    boolean write(CacheLookup lookup, String generation, Value value, Duration ttl, long maxEntries, long maxBytes) {
        if (ttl == null || ttl.compareTo(Duration.ofMillis(1)) < 0 || ttl.compareTo(Duration.ofDays(1)) > 0
                || maxEntries < 1 || maxEntries > 10_000 || maxBytes < 1 || maxBytes > 64L * 1024 * 1024) {
            throw new IllegalArgumentException("Redis cache requires bounded TTL and region quotas");
        }
        String wire = value.absent() ? "N" : "V" + Base64.getEncoder().encodeToString(value.payload());
        Long result = redis.execute(WRITE, List.of(epochKey(lookup.descriptor().id(), lookup.tenant())),
                generation, dataKey(lookup, generation), wire, Long.toString(ttl.toMillis()), Long.toString(maxEntries), Long.toString(maxBytes));
        return Long.valueOf(1).equals(result);
    }

    void invalidate(String region, long tenant) {
        redis.execute(SYNC, List.of(epochKey(region, tenant)), "0000000000000000000", UUID.randomUUID().toString(), Long.toString(IDLE_TTL.toMillis()), "1");
    }

    void evictCorrupt(CacheLookup lookup, String generation) {
        redis.execute(EVICT, List.of(epochKey(lookup.descriptor().id(), lookup.tenant())), generation, dataKey(lookup, generation));
    }

    Stats snapshot(String region, long tenant) {
        var result = redis.execute(STATS, List.of(epochKey(region, tenant)));
        return new Stats(((Number) result.get(0)).longValue(), ((Number) result.get(1)).longValue());
    }

    String epochKey(String region, long tenant) {
        if (region == null || region.length() > 96 || !region.matches("[a-z]+\\.[a-z][a-z0-9-]{0,79}") || tenant < 1) {
            throw new IllegalArgumentException("Invalid Redis cache scope");
        }
        String tag = CacheLookup.hash(namespace + ":" + region + ":" + tenant);
        return "sma:" + namespace + ":cache:v2:{" + tag + "}:" + region + ":t" + tenant;
    }
    String revisionKey(String region, long tenant) { return epochKey(region, tenant); }

    /** Hash field, not a standalone Redis key. Generation is checked atomically against this hash's metadata. */
    String dataKey(CacheLookup lookup, String generation) {
        if (generation == null || !generation.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid cache generation");
        return "d:v" + lookup.descriptor().schemaVersion() + ":p" + CacheLookup.hash(lookup.portal()) + ":u" + lookup.user()
                + ":c" + CacheLookup.hash(lookup.valueType().getName()) + ":k" + lookup.digest();
    }
}
