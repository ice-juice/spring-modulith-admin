package com.sma.admin.monitor.internal.persistence;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;
import com.sma.admin.monitor.DatabaseSchema;
import com.sma.admin.monitor.DatabaseSchema.*;

/** JDBC schema metadata only: no table contents, arbitrary SQL or connection credentials. */
@Repository
public class SchemaReader {
 private final DataSource source;public SchemaReader(DataSource source){this.source=source;}
 public DatabaseSchema read()throws SQLException{
  try(var connection=source.getConnection()){
   var metadata=connection.getMetaData();String catalog=connection.getCatalog();List<Table> tables=new ArrayList<>();
   try(var rows=metadata.getTables(catalog,null,"%",new String[]{"TABLE"})){
    while(rows.next())tables.add(table(metadata,catalog,rows.getString("TABLE_NAME"),rows.getString("REMARKS")));
   }
   tables.sort(Comparator.comparing(Table::name));return new DatabaseSchema(catalog,metadata.getDatabaseProductName(),metadata.getDatabaseProductVersion(),List.copyOf(tables));
  }
 }
 private Table table(DatabaseMetaData metadata,String catalog,String name,String remarks)throws SQLException{
  Set<String> primary=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);try(var rows=metadata.getPrimaryKeys(catalog,null,name)){while(rows.next())primary.add(rows.getString("COLUMN_NAME"));}
  String escape=metadata.getSearchStringEscape();String pattern=name.replace(escape,escape+escape).replace("_",escape+"_").replace("%",escape+"%");
  List<Column> columns=new ArrayList<>();try(var rows=metadata.getColumns(catalog,null,pattern,"%")){
   while(rows.next()){
    String column=rows.getString("COLUMN_NAME"),type=rows.getString("TYPE_NAME");
    if(Set.of("CHAR","VARCHAR","BINARY","VARBINARY").contains(type))type+="("+rows.getInt("COLUMN_SIZE")+")";
    else if(Set.of("DECIMAL","NUMERIC").contains(type))type+="("+rows.getInt("COLUMN_SIZE")+","+rows.getInt("DECIMAL_DIGITS")+")";
    else if(Set.of("DATETIME","TIMESTAMP","TIME").contains(type)&&rows.getInt("DECIMAL_DIGITS")>0)type+="("+rows.getInt("DECIMAL_DIGITS")+")";
    String value=rows.getString("COLUMN_DEF");if(column.toLowerCase(Locale.ROOT).matches(".*(password|secret|token).*"))value=null;
    columns.add(new Column(column,type,rows.getInt("NULLABLE")!=DatabaseMetaData.columnNoNulls,value,rows.getString("REMARKS"),primary.contains(column)));
   }
  }
  Map<String,TreeMap<Integer,String>> names=new TreeMap<>();Map<String,Boolean> unique=new HashMap<>();
  try(var rows=metadata.getIndexInfo(catalog,null,name,false,false)){
   while(rows.next()){
    String index=rows.getString("INDEX_NAME"),column=rows.getString("COLUMN_NAME");if(index==null||column==null)continue;
    names.computeIfAbsent(index,key->new TreeMap<>()).put(rows.getInt("ORDINAL_POSITION"),column);unique.put(index,!rows.getBoolean("NON_UNIQUE"));
   }
  }
  var indexes=names.entrySet().stream().map(entry->new Index(entry.getKey(),unique.get(entry.getKey()),List.copyOf(entry.getValue().values()))).toList();
  return new Table(name,remarks,List.copyOf(columns),indexes);
 }
}
