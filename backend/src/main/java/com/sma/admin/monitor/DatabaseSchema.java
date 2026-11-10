package com.sma.admin.monitor;
import java.util.List;
public record DatabaseSchema(String database,String product,String version,List<Table> tables){
 public DatabaseSchema{tables=List.copyOf(tables);}
 public record Column(String name,String type,boolean nullable,String defaultValue,String remarks,boolean primaryKey){}
 public record Index(String name,boolean unique,List<String> columns){public Index{columns=List.copyOf(columns);}}
 public record Table(String name,String remarks,List<Column> columns,List<Index> indexes){public Table{columns=List.copyOf(columns);indexes=List.copyOf(indexes);}}
}
