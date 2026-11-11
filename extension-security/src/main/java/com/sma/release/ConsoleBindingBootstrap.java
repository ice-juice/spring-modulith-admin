package com.sma.release;

import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.sma.extension.protocol.*;
import com.sma.release.auth.ConsoleIdentityService;
import com.sma.release.auth.SecurityRecords.User;
import com.sma.release.store.ReleaseDatabase;

/** Operator-only initial identity binding. Never accepts reassignment or host-controlled provisioning. */
@Component @Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE)
public class ConsoleBindingBootstrap implements ApplicationRunner {
    private final String file;private final ReleaseDatabase database;private final ConsoleIdentityService consoles;
    public ConsoleBindingBootstrap(@Value("${release.console-binding-file:}")String file,ReleaseDatabase database,ConsoleIdentityService consoles){this.file=file;this.database=database;this.consoles=consoles;}
    public record Input(String machineId,ConsoleIdentityService.Subject subject,String releaseUsername) {}
    @Override public void run(ApplicationArguments arguments)throws Exception {
        if(file.isBlank())return;
        Path path=Path.of(file).toAbsolutePath().normalize();
        Checks.require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) && Files.size(path)<=16384,"console-binding-file-required");
        LocalBootstrap.requirePrivate(path);var input=ProtocolJson.read(Files.readAllBytes(path),Input.class);
        database.transaction(tx->{
            var user=tx.list("user",User.class).stream().filter(u->u.username().equals(input.releaseUsername())).findFirst().orElseThrow(()->new ProtocolException("user-not-found"));
            String id=ConsoleIdentityService.bindingId(input.machineId(),input.subject());
            var old=tx.get("console-binding",id,ConsoleIdentityService.Binding.class);
            if(old.isPresent())Checks.require(old.get().enabled() && old.get().userId().equals(user.id()) && old.get().revision()==1,"console-binding-bootstrap-conflict");
            else consoles.bindLocal(tx,"local-console-bootstrap",new ConsoleIdentityService.Bind(input.machineId(),input.subject(),user.id(),0,true));
            return null;
        });
        Files.delete(path);
    }
}
