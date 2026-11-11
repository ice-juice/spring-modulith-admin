package com.sma.admin.mail;

import static org.assertj.core.api.Assertions.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MailDeliveryCompatibilityTests {
    @TempDir Path directory;
    @Test void preGuardCompiledCommandStillLinksAndItsJsonDoesNotAddANullField()throws Exception{
        var root=directory.resolve("com/sma/admin/mail");Files.createDirectories(root);var old=root.resolve("MailSendApi.java");
        Files.writeString(old,"""
                package com.sma.admin.mail;
                public interface MailSendApi {
                    record Command(String application,String scene,String templateCode,String language,String recipient,
                        java.util.Map<String,String> variables,java.time.Instant expiresAt,String idempotencyKey) {}
                }
                """);
        var client=directory.resolve("LegacyMailCommand.java");Files.writeString(client,"""
                public class LegacyMailCommand {
                    public static com.sma.admin.mail.MailSendApi.Command create() {
                        return new com.sma.admin.mail.MailSendApi.Command("shop","notice","receipt","en-US","to@example.test",
                            java.util.Map.of(),java.time.Instant.EPOCH,"original-key");
                    }
                }
                """);
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,null,null,"-proc:none","-d",directory.toString(),old.toString(),client.toString())).isZero();
        try(var loader=new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()},MailSendApi.class.getClassLoader())){
            var actual=(MailSendApi.Command)loader.loadClass("LegacyMailCommand").getMethod("create").invoke(null);
            assertThat(actual.guard()).isNull();assertThat(actual.idempotencyKey()).isEqualTo("original-key");
            assertThat(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(actual)).doesNotContain("\"guard\"");
        }
    }
    @Test void preReceiptCompiledConsumerStillLinksToTheOriginalConstructorAndAccessors()throws Exception {
        var root=directory.resolve("com/sma/admin/mail");Files.createDirectories(root);
        var old=root.resolve("MailDeliveryView.java");
        Files.writeString(old,"""
                package com.sma.admin.mail;
                public record MailDeliveryView(Long id,String application,String scene,String recipientMasked,
                    String state,int attempts,java.time.Instant expiresAt,String lastResult) {}
                """);
        var client=directory.resolve("LegacyMailConsumer.java");
        Files.writeString(client,"""
                public class LegacyMailConsumer {
                    public static com.sma.admin.mail.MailDeliveryView create() {
                        var row=new com.sma.admin.mail.MailDeliveryView(1L,"shop","notice","r***@example.test",
                            "ACCEPTED",1,java.time.Instant.EPOCH,"ACCEPTED");
                        if(!row.state().equals("ACCEPTED") || row.id()!=1L)throw new IllegalStateException();
                        return row;
                    }
                }
                """);
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,null,null,"-proc:none","-d",directory.toString(),old.toString(),client.toString())).isZero();
        // Parent-first loading supplies the current production DTO, while the consumer bytecode is compiled against the old DTO.
        try(var loader=new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()},MailDeliveryView.class.getClassLoader())) {
            var actual=(MailDeliveryView)loader.loadClass("LegacyMailConsumer").getMethod("create").invoke(null);
            assertThat(actual.receiptState()).isNull();assertThat(actual.state()).isEqualTo("ACCEPTED");
        }
    }
}
