package com.sma.release;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import com.sma.extension.protocol.*;
import com.sma.release.auth.IdentityService;
import com.sma.release.auth.MachineService;
import com.sma.release.auth.SecurityRecords;
import com.sma.release.crypto.SigningService;
import com.sma.release.crypto.SecretVault;
import com.sma.release.store.ReleaseDatabase;

/** One-time file provisioned by an operator. Never accepts bootstrap secrets via an HTTP endpoint or prints them. */
@Component
@org.springframework.core.annotation.Order(0)
public class LocalBootstrap implements ApplicationRunner {
    private final ReleaseProperties properties; private final ReleaseDatabase database; private final IdentityService identities;
    private final MachineService machines; private final SigningService signing;private final SecretVault vault;
    public LocalBootstrap(ReleaseProperties properties,ReleaseDatabase database,IdentityService identities,MachineService machines,SigningService signing,SecretVault vault) {
        this.properties=properties;this.database=database;this.identities=identities;this.machines=machines;this.signing=signing;this.vault=vault;
    }
    public record Bootstrap(String username,String loginPassword,String operationPassword,List<MachineInput> machines,String resultFile) {
        public Bootstrap { machines=List.copyOf(machines); }
        @Override public String toString() { return "Bootstrap[redacted]"; }
    }
    public record MachineInput(String id,MachineService.Kind kind,Set<String> environments,String token) {
        public MachineInput { environments=Set.copyOf(environments); }
        @Override public String toString() { return "MachineInput[redacted]"; }
    }
    public record Result(SecurityRecords.NewAccount account,List<SigningService.PublicTrustKey> keys) {}
    private record Receipt(String inputHash,String sealedResult) {}
    @Override public void run(ApplicationArguments args) throws Exception {
        if(properties.bootstrapFile()==null || properties.bootstrapFile().isBlank()) return;
        Path input=Path.of(properties.bootstrapFile()).toAbsolutePath().normalize();
        Checks.require(Files.isRegularFile(input,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(input),"bootstrap-file-required");
        requirePrivate(input);
        Checks.require(Files.size(input)<=65536,"bootstrap-file-too-large");
        var request=ProtocolJson.read(Files.readAllBytes(input),Bootstrap.class);
        Path output=Path.of(request.resultFile()).toAbsolutePath().normalize();
        Checks.require(!output.equals(input),"bootstrap-output-conflict");
        Files.createDirectories(output.getParent());
        Path staging=Files.getFileStore(output.getParent()).supportsFileAttributeView("posix")
                ?Files.createTempFile(output.getParent(),"bootstrap-",".partial",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                :Files.createTempFile(output.getParent(),"bootstrap-",".partial");
        requirePrivate(staging);
        try {
            String inputHash=Hashes.sha256(ProtocolJson.write(request));
            Result result=database.transaction(tx -> {
                var receipt=tx.get("bootstrap-receipt","initial",Receipt.class);
                if(receipt.isPresent()) {
                    Checks.require(receipt.get().inputHash().equals(inputHash),"bootstrap-already-completed");
                    var previous=ProtocolJson.read(vault.open("bootstrap-receipt",receipt.get().sealedResult()),Result.class);
                    if(tx.now().isBefore(previous.account().expiresAt()))return previous;
                    // Never silently replace an operator-owned output. Remove/archive the expired protected result first.
                    Checks.require(!Files.exists(output,LinkOption.NOFOLLOW_LINKS),"bootstrap-expired-result-must-be-removed");
                    var renewed=identities.renewBootstrapEnrollment(tx,previous.account(),request.username(),request.loginPassword(),request.operationPassword());
                    var renewedResult=new Result(renewed,signing.publicKeys(tx));
                    tx.put("bootstrap-receipt","initial",new Receipt(inputHash,vault.seal("bootstrap-receipt",ProtocolJson.write(renewedResult))));return renewedResult;
                }
                Checks.require(!Files.exists(output,LinkOption.NOFOLLOW_LINKS),"bootstrap-output-exists");
                Checks.require(tx.list("user",SecurityRecords.User.class).isEmpty(),"bootstrap-already-completed");
                var account=identities.createAccount(tx,"local-bootstrap",request.username(),request.loginPassword(),request.operationPassword(),true);
                for(var machine:request.machines()) machines.provision(tx,machine.id(),machine.kind(),machine.environments(),machine.token());
                signing.initialize(tx);var created=new Result(account,signing.publicKeys(tx));
                tx.put("bootstrap-receipt","initial",new Receipt(inputHash,vault.seal("bootstrap-receipt",ProtocolJson.write(created))));return created;
            });
            byte[] bytes=ProtocolJson.write(result);
            Files.write(staging,bytes,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            try(var channel=java.nio.channels.FileChannel.open(staging,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) { channel.force(true); }
            if(Files.exists(output,LinkOption.NOFOLLOW_LINKS)) {
                Checks.require(Files.isRegularFile(output,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(output) && Files.size(output)==bytes.length,"bootstrap-output-conflict");
                requirePrivate(output);Checks.require(Hashes.sha256(Files.readAllBytes(output)).equals(Hashes.sha256(bytes)),"bootstrap-output-conflict");
            } else Files.move(staging,output);
            // Input contains passwords; remove after durable initialization, retaining only the protected enrollment result.
            Files.delete(input);
        } finally { Files.deleteIfExists(staging); }
    }
    static void requirePrivate(Path file) throws Exception {
        if(Files.getFileStore(file).supportsFileAttributeView("posix")) {
            var perms=Files.getPosixFilePermissions(file,LinkOption.NOFOLLOW_LINKS);
            Checks.require(perms.stream().noneMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_")),"bootstrap-file-permissions-too-wide");
        } else {
            var view=Files.getFileAttributeView(file,java.nio.file.attribute.AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
            Checks.require(view!=null,"bootstrap-file-acl-unavailable");
            var owner=view.getOwner();
            for(var entry:view.getAcl()) if(entry.type()==java.nio.file.attribute.AclEntryType.ALLOW && entry.permissions().contains(java.nio.file.attribute.AclEntryPermission.READ_DATA)) {
                String name=entry.principal().getName().toLowerCase(java.util.Locale.ROOT);
                Checks.require(entry.principal().equals(owner) || name.endsWith("\\system") || name.endsWith("\\administrators"),"bootstrap-file-permissions-too-wide");
            }
        }
    }
}
