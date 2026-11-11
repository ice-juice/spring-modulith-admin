package com.sma.worker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.sma.extension.protocol.*;

/** Scans an authoritative CycloneDX inventory using a pinned Grype image and an offline database. */
public final class GrypeAdapter {
    private final WorkerSettings settings;
    private final ContainerRunner runner;
    private final Clock clock;
    public GrypeAdapter(WorkerSettings settings,ContainerRunner runner,Clock clock) {this.settings=settings;this.runner=runner;this.clock=clock;}
    public record DatabaseRefresh(Instant synchronizedAt) {}
    public void refreshDatabase() throws IOException,InterruptedException {
        Files.createDirectories(settings.scannerDatabase());Files.createDirectories(settings.workDirectory());
        Path log=Files.createTempFile(settings.workDirectory(),"db-update-",".log");
        try {
            runner.run(settings.scannerImage(),null,List.of("db","update"),List.of(new ContainerRunner.Mount(settings.scannerDatabase(),"/db",false)),true,Duration.ofMinutes(10),log);
            try(var paths=Files.list(settings.scannerDatabase())) {
                Checks.require(paths.anyMatch(p -> p.getFileName().toString().endsWith(".db") || Files.isDirectory(p)),"scanner-database-missing");
            }
            Path stamp=settings.scannerDatabase().resolve("sma-refresh.json");
            Checks.require(!Files.isSymbolicLink(stamp),"unsafe-scanner-cache");
            Files.write(stamp,ProtocolJson.write(new DatabaseRefresh(clock.instant())));
        } finally {Files.deleteIfExists(log);}
    }
    public ScanReport scan(ArchiveSnapshot snapshot,Path jobDirectory) throws IOException,InterruptedException {
        Path stamp=settings.scannerDatabase().resolve("sma-refresh.json");
        Checks.require(Files.isRegularFile(stamp,LinkOption.NOFOLLOW_LINKS) && Files.size(stamp)<=4096,"scanner-database-refresh-required");
        var refresh=ProtocolJson.read(Files.readAllBytes(stamp),DatabaseRefresh.class);
        Checks.require(!refresh.synchronizedAt().isAfter(clock.instant()) && clock.instant().isBefore(refresh.synchronizedAt().plus(Duration.ofHours(24))),"stale-scanner-database");
        Path input=Files.createDirectories(jobDirectory.resolve("scan-input"));
        Files.write(input.resolve("inventory.json"),inventory(snapshot));
        byte[] report=runner.runBinary(settings.scannerImage(),null,List.of("sbom:/input/inventory.json","--output","json"),
                List.of(new ContainerRunner.Mount(input,"/input",true),new ContainerRunner.Mount(settings.scannerDatabase(),"/db",true)),
                Duration.ofMinutes(10),jobDirectory.resolve("scanner.log"),ProtocolJson.MAX_DOCUMENT_BYTES);
        return parse(report,snapshot,refresh.synchronizedAt(),clock.instant());
    }
    public static byte[] inventory(ArchiveSnapshot snapshot) {
        var components=new ArrayList<Map<String,Object>>();
        for(var dependency:snapshot.dependencyCoordinates().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            String[] coordinate=dependency.getKey().split(":",-1);
            String purl="pkg:maven/"+coordinate[0]+"/"+coordinate[1]+"@"+coordinate[2];
            components.add(Map.of("type","library","group",coordinate[0],"name",coordinate[1],"version",coordinate[2],"purl",purl,
                    "bom-ref",dependency.getValue(),"hashes",List.of(Map.of("alg","SHA-256","content",dependency.getValue()))));
        }
        // Application bytes are inspected separately; declared dependencies must cover every embedded archive.
        return ProtocolJson.write(Map.of("bomFormat","CycloneDX","specVersion","1.5","version",1,"components",components,
                "metadata",Map.of("component",Map.of("type","application","name",snapshot.manifest().pluginId(),"version",snapshot.manifest().version(),
                        "hashes",List.of(Map.of("alg","SHA-256","content",snapshot.sha256()))))));
    }
    @SuppressWarnings("unchecked")
    public static ScanReport parse(byte[] json,ArchiveSnapshot snapshot,Instant refreshedAt,Instant now) {
        Map<String,Object> document=ProtocolJson.read(json,Map.class);
        var descriptor=map(document.get("descriptor"));
        Checks.require("grype".equals(descriptor.get("name")) && descriptor.get("version") instanceof String,"unsupported-scanner-report");
        var db=map(descriptor.get("db"));
        Checks.require(db.get("error")==null || "".equals(db.get("error")),"scanner-database-error");
        Checks.require(document.get("matches") instanceof List<?>,"scanner-results-incomplete");
        var matches=new ArrayList<Object>((List<?>)document.get("matches"));
        // Ignore rules in the tool must never hide findings from the platform's policy gate.
        if(document.get("ignoredMatches") instanceof List<?> ignored) matches.addAll(ignored);
        List<ScanReport.Finding> findings=new ArrayList<>();
        for(Object value:matches) {
            var match=map(value);var vulnerability=map(match.get("vulnerability"));var artifact=map(match.get("artifact"));
            String purl=String.valueOf(artifact.get("purl"));String basePurl=purl.split("\\?",2)[0];
            var dependency=snapshot.dependencyCoordinates().entrySet().stream().filter(d -> {
                String[] parts=d.getKey().split(":",-1);return basePurl.equals("pkg:maven/"+parts[0]+"/"+parts[1]+"@"+parts[2]);
            }).findFirst()
                    .orElseThrow(() -> new ProtocolException("unmapped-scanner-component"));
            String severity=String.valueOf(vulnerability.get("severity")).toUpperCase(java.util.Locale.ROOT);
            ScanReport.Finding.Severity level;
            try {level=ScanReport.Finding.Severity.valueOf(severity);}catch(IllegalArgumentException unknown){level=ScanReport.Finding.Severity.UNKNOWN;}
            Double score=null;
            if(vulnerability.get("cvss") instanceof List<?> ratings) for(Object rating:ratings) {
                Object number=map(map(rating).get("metrics")).get("baseScore");
                if(number instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue()>=0 && n.doubleValue()<=10)
                    score=score==null?n.doubleValue():Math.max(score,n.doubleValue());
            }
            boolean exploited=vulnerability.get("knownExploited") instanceof List<?> known && !known.isEmpty()
                    || Boolean.TRUE.equals(vulnerability.get("knownExploited"));
            String id=String.valueOf(vulnerability.get("id"));Checks.require(!id.equals("null"),"scanner-id-required");
            findings.add(new ScanReport.Finding(id,dependency.getValue(),score,level,exploited,true,false,"grype:"+Hashes.sha256(ProtocolJson.write(match))));
        }
        return new ScanReport(snapshot.sha256(),"grype",descriptor.get("version").toString(),now,refreshedAt,true,findings,snapshot.inventory().stream().sorted().toList());
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) {
        Checks.require(value instanceof Map<?,?>,"scanner-results-incomplete");return (Map<String,Object>)value;
    }
}
