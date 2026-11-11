package com.sma.admin.common.internal.persistence;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;

/** Only this fixed framework row is accessed; no tenant or business-table SQL is available. */
final class MySqlIdBlockSource {
    static final long BLOCK_SIZE=1024;
    record Block(long first,long exclusiveEnd) {}
    @FunctionalInterface interface Connections {Connection open() throws SQLException;}
    private final Connections connections;
    MySqlIdBlockSource(Connections connections){this.connections=connections;}
    MySqlIdBlockSource(DataSource source){this(connections(source));}
    Block reserve(){
        try(var connection=connections.open()){
            connection.setNetworkTimeout(Runnable::run,5000);connection.setReadOnly(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);connection.setAutoCommit(false);
            try{
                long first;
                try(var select=connection.prepareStatement("SELECT next_id FROM common_id_block WHERE id=1 AND deleted=0 FOR UPDATE")){
                    select.setQueryTimeout(2);
                    try(var result=select.executeQuery()){
                        if(!result.next())throw unavailable();first=result.getLong(1);
                        if(result.wasNull()||first<1||first>Long.MAX_VALUE-BLOCK_SIZE)throw unavailable();
                    }
                }
                long end=Math.addExact(first,BLOCK_SIZE);
                try(var update=connection.prepareStatement("UPDATE common_id_block SET next_id=?,updated_at=UTC_TIMESTAMP(3),updated_by=0 WHERE id=1 AND next_id=? AND deleted=0")){
                    update.setQueryTimeout(2);update.setLong(1,end);update.setLong(2,first);
                    if(update.executeUpdate()!=1)throw unavailable();
                }
                connection.commit();return new Block(first,end);
            }catch(SQLException|RuntimeException failure){
                try{connection.rollback();}catch(SQLException ignored){/* Commit/rollback may be uncertain: discard the entire range. */}
                throw unavailable();
            }
        }catch(SQLException failure){throw unavailable();}
    }
    private static Connections connections(DataSource source){
        try{
            var pool=source.unwrap(HikariDataSource.class);String url=pool.getJdbcUrl();
            if(url==null||!url.startsWith("jdbc:mysql://"))throw unavailable();
            // A short connection must not silently select a different catalog than the primary pool.
            if(pool.getCatalog()!=null||pool.getSchema()!=null||pool.getConnectionInitSql()!=null)throw unavailable();
            var properties=new Properties();properties.putAll(pool.getDataSourceProperties());
            if(pool.getUsername()!=null)properties.setProperty("user",pool.getUsername());
            if(pool.getPassword()!=null)properties.setProperty("password",pool.getPassword());
            validateUrl(url);
            if(properties.keySet().stream().anyMatch(key->key.toString().equalsIgnoreCase("useConfigs")))throw unavailable();
            var controlled=Set.of("connecttimeout","sockettimeout","autoreconnect","autoreconnectforpools","reconnectattxend");
            properties.keySet().removeIf(key->controlled.contains(key.toString().toLowerCase(Locale.ROOT)));
            properties.setProperty("connectTimeout","2000");properties.setProperty("socketTimeout","5000");
            properties.setProperty("autoReconnect","false");properties.setProperty("autoReconnectForPools","false");properties.setProperty("reconnectAtTxEnd","false");
            // Preserve all other authentication, TLS and session settings of the primary pool.
            return ()->DriverManager.getConnection(url,(Properties)properties.clone());
        }catch(SQLException|IllegalArgumentException invalid){throw unavailable();}
    }
    private static void validateUrl(String url){
        String query=URI.create(url.substring(5)).getRawQuery();if(query==null)return;
        for(String pair:query.split("&")){
            var parts=pair.split("=",2);String name=URLDecoder.decode(parts[0],StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            String value=parts.length==2?URLDecoder.decode(parts[1],StandardCharsets.UTF_8):"";
            if(name.equals("useconfigs"))throw unavailable();
            if(Set.of("autoreconnect","autoreconnectforpools","reconnectattxend").contains(name)&&!Set.of("false","0").contains(value.toLowerCase(Locale.ROOT)))throw unavailable();
            if(name.equals("connecttimeout")||name.equals("sockettimeout")){
                int millis=Integer.parseInt(value);if(millis<1||millis>(name.equals("connecttimeout")?2000:5000))throw unavailable();
            }
        }
    }
    static IllegalStateException unavailable(){return new IllegalStateException("Shared ID allocation unavailable; no fallback generator is permitted");}
}
