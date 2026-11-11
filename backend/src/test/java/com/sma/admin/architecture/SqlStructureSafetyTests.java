package com.sma.admin.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sma.admin.common.BizException;
import com.sma.admin.common.PageQuery;

/** Structural source gate complements bound-value integration tests; not a SQL attack detector. */
class SqlStructureSafetyTests {
    @Test void mapperSqlCannotInterpolateUntrustedText() throws Exception {
        var problems = new ArrayList<String>();
        for (Path root : new Path[]{Path.of("src/main/java"), Path.of("src/main/resources")}) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith("Mapper.java") && !name.endsWith(".xml")) continue;
                    String source = Files.readString(file);
                    if ((name.endsWith("Mapper.java") || source.contains("<mapper")) && source.contains("${")) problems.add(file.toString());
                }
            }
        }
        assertThat(problems).as("SQL values must use bound parameters; identifiers require fixed mappings").isEmpty();
    }
    @Test void sortInjectionIsRejectedWithoutRejectingValidKnownSorts() {
        for (String sort : new String[]{"name desc; DROP TABLE iam_user", "name/**/", "(select 1)", "name, id"})
            assertThatThrownBy(() -> new PageQuery(1, 20, sort, "asc").toPage(Map.of("name", "name"))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> new PageQuery(1, 20, "name", "asc;select 1").toPage(Map.of("name", "name"))).isInstanceOf(BizException.class);
        assertThat(new PageQuery(1, 20, "name", "desc").toPage(Map.of("name", "name")).orders()).hasSize(1);
    }
}
