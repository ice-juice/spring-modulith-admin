package com.sma.admin.identity;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationServicesExampleCompilationTests {
    @TempDir Path output;
    @Test void standaloneBusinessExampleCompilesAgainstOnlySupportedPublicContracts()throws Exception{
        Path root=Path.of("../examples/integration-services/src/main/java");if(!Files.isDirectory(root))root=Path.of("examples/integration-services/src/main/java");
        assertThat(root).isDirectory();List<Path> files;
        try(var sources=Files.walk(root)){files=sources.filter(path->path.toString().endsWith(".java")).sorted().toList();}
        assertThat(files).hasSize(7);
        for(var source:files)assertThat(Files.readString(source)).doesNotContain("com.sma.admin.identity.internal","com.sma.admin.mail.internal","com.sma.admin.file.internal");
        var args=new ArrayList<>(List.of("-proc:none","--release","21","-encoding","UTF-8","-classpath",System.getProperty("java.class.path"),"-d",output.toString()));
        files.forEach(path->args.add(path.toString()));
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new))).isZero();
    }
}
