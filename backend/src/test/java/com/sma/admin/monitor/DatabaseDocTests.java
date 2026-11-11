package com.sma.admin.monitor;
import static org.assertj.core.api.Assertions.*;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import com.sma.admin.monitor.internal.service.DatabaseDocService;
import com.sma.admin.support.IntegrationTest;
@IntegrationTest
class DatabaseDocTests {
 @Autowired DatabaseDocService service;
 @Test void metadataContainsActualColumnsAndIndexesAndExportsBothLanguages()throws Exception{
  var schema=service.schema();
  String expectedDatabase=java.net.URI.create(com.sma.admin.support.TestDatabase.URL.substring(5)).getPath().substring(1);
  assertThat(schema.database()).matches("springboot_modulith_admin_test|sma_test_[a-z0-9_]+").isEqualTo(expectedDatabase);
  assertThat(schema.tables()).anySatisfy(table->{assertThat(table.name()).isEqualToIgnoringCase("iam_user");
   assertThat(table.columns()).anySatisfy(column->{assertThat(column.name()).isEqualTo("avatar_file_id");assertThat(column.nullable()).isTrue();});
   assertThat(table.indexes()).anySatisfy(index->{assertThat(index.name()).isEqualTo("idx_iam_user_avatar");assertThat(index.columns()).contains("tenant_id","avatar_file_id");});
  });
  try{LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);assertThat(service.markdown()).contains("数据库结构文档","字段","iam_user");
   LocaleContextHolder.setLocale(Locale.US);assertThat(service.markdown()).contains("Database schema documentation","Nullable","avatar_file_id").doesNotContain("sma_dev","jdbc:mysql");
  }finally{LocaleContextHolder.resetLocaleContext();}
 }
}
