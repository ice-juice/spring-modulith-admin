package com.sma.admin.common.internal.persistence;

import static org.assertj.core.api.Assertions.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import com.sma.admin.support.TestDatabase;

/** Explicit standalone migration run only: no live application may use its guarded disposable schema. */
class GlobalIdMigrationIT {
    private static final long LEGACY=8_000_000_000_000_000_000L;
    @Test void preservesLegacyIdsAndAllocatesDisjointDurableRangesWithoutClockOrWorkerIds() throws Exception {
        TestDatabase.clean();migration("12").migrate();
        try(var connection=open();var statement=connection.createStatement()){
            statement.execute("CREATE TABLE shop_legacy_fixture(id BIGINT NOT NULL PRIMARY KEY,payload VARCHAR(32) NOT NULL)");
            statement.executeUpdate("INSERT INTO shop_legacy_fixture VALUES("+LEGACY+",'keep-me'),(42,'small-id')");
        }
        migration("latest").migrate();migration("latest").validate();assertThat(floor()).isEqualTo(LEGACY+1);
        var a=generator();var b=generator();var ids=ConcurrentHashMap.<Long>newKeySet();
        try(var pool=Executors.newFixedThreadPool(8)){
            var futures=new ArrayList<Future<?>>();for(int thread=0;thread<8;thread++){var node=thread%2==0?a:b;futures.add(pool.submit(()->{
                for(int i=0;i<2048;i++)assertThat(ids.add(node.nextId(null).longValue())).isTrue();
            }));}
            for(var future:futures)future.get(45,TimeUnit.SECONDS);
        }
        assertThat(ids).hasSize(16384).allSatisfy(id->assertThat(id).isGreaterThan(LEGACY));
        assertThat(generator().nextId(null).longValue()).isGreaterThan(Collections.max(ids));

        long before=floor();var loseAcknowledgement=new AtomicBoolean(true);
        var uncertain=new DatabaseIdentifierGenerator(new MySqlIdBlockSource(()->{
            var real=open();return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if(method.getName().equals("commit")){
                    real.commit();if(loseAcknowledgement.getAndSet(false))throw new SQLException("private-endpoint-must-not-leak");return null;
                }
                try{return method.invoke(real,args);}catch(InvocationTargetException failure){throw failure.getCause();}
            });
        }));
        assertThatThrownBy(()->uncertain.nextId(null)).isInstanceOf(IllegalStateException.class).hasNoCause().hasMessageNotContaining("private-endpoint");
        assertThat(floor()).isEqualTo(before+1024);assertThat(uncertain.nextId(null).longValue()).isEqualTo(before+1024);

        long burned;
        try(var transaction=open()){
            transaction.setAutoCommit(false);
            try(var statement=transaction.prepareStatement("UPDATE shop_legacy_fixture SET payload='rollback' WHERE id=?")){statement.setLong(1,LEGACY);statement.executeUpdate();}
            burned=generator().nextId(null).longValue();transaction.rollback();
        }
        assertThat(generator().nextId(null).longValue()).isGreaterThan(burned);
        try(var connection=open();var statement=connection.createStatement();var result=statement.executeQuery("SELECT payload FROM shop_legacy_fixture ORDER BY id")){
            assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("small-id");
            assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("keep-me");assertThat(result.next()).isFalse();
        }
        try(var connection=open();var statement=connection.prepareStatement("UPDATE common_id_block SET next_id=? WHERE id=1")){statement.setLong(1,Long.MAX_VALUE);statement.executeUpdate();}
        assertThatThrownBy(()->generator().nextId(null)).isInstanceOf(IllegalStateException.class);assertThat(floor()).isEqualTo(Long.MAX_VALUE);
        TestDatabase.clean();migration("latest").migrate();migration("latest").validate();
        assertThat(floor()).isEqualTo(4_503_599_627_370_496L);assertThat(generator().nextId(null).longValue()).isEqualTo(floor()-1024);
    }
    private DatabaseIdentifierGenerator generator(){return new DatabaseIdentifierGenerator(new MySqlIdBlockSource(this::open));}
    private Connection open() throws SQLException{return DriverManager.getConnection(TestDatabase.URL,TestDatabase.USERNAME,TestDatabase.PASSWORD);}
    private long floor() throws SQLException{try(var connection=open();var statement=connection.createStatement();var result=statement.executeQuery("SELECT next_id FROM common_id_block WHERE id=1")){assertThat(result.next()).isTrue();return result.getLong(1);}}
    private Flyway migration(String target){return Flyway.configure().dataSource(TestDatabase.URL,TestDatabase.USERNAME,TestDatabase.PASSWORD).locations("classpath:db/migration/common").table("flyway_schema_history_common").target(target).load();}
}
