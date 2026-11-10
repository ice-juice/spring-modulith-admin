package com.sma.admin.monitor.internal.service;
import java.sql.SQLException;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import com.sma.admin.monitor.internal.persistence.SchemaReader;
@Service
public class DatabaseDocService {
 private final SchemaReader reader;private final MessageSource messages;
 public DatabaseDocService(SchemaReader reader,MessageSource messages){this.reader=reader;this.messages=messages;}
 public com.sma.admin.monitor.DatabaseSchema schema()throws SQLException{return reader.read();}
 private String text(String key){return messages.getMessage("monitor.doc."+key,null,LocaleContextHolder.getLocale());}
 private String safe(String value){return value==null?"":value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("|","\\|").replace("\r"," ").replace("\n"," ");}
 public String markdown()throws SQLException{
  var schema=schema();var out=new StringBuilder("# ").append(text("title")).append("\n\n").append(safe(schema.database())).append(" · ").append(safe(schema.product())).append(' ').append(safe(schema.version())).append("\n\n");
  for(var table:schema.tables()){
   out.append("## ").append(safe(table.name())).append("\n\n").append(safe(table.remarks())).append("\n\n");
   out.append("| ").append(text("column")).append(" | ").append(text("type")).append(" | ").append(text("nullable")).append(" | ").append(text("default")).append(" | ").append(text("primary")).append(" | ").append(text("remarks")).append(" |\n|---|---|---|---|---|---|\n");
   for(var column:table.columns())out.append("| ").append(safe(column.name())).append(" | ").append(safe(column.type())).append(" | ").append(text(column.nullable()?"yes":"no")).append(" | ").append(safe(column.defaultValue())).append(" | ").append(text(column.primaryKey()?"yes":"no")).append(" | ").append(safe(column.remarks())).append(" |\n");
   out.append("\n| ").append(text("index")).append(" | ").append(text("unique")).append(" | ").append(text("columns")).append(" |\n|---|---|---|\n");
   for(var index:table.indexes())out.append("| ").append(safe(index.name())).append(" | ").append(text(index.unique()?"yes":"no")).append(" | ").append(safe(String.join(", ",index.columns()))).append(" |\n");
   out.append('\n');
  }
  return out.toString();
 }
}
