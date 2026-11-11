package com.sma.admin.common.internal;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.sql.DriverManager;
import java.util.concurrent.*;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.sma.admin.common.security.TenantScope;
import com.sma.admin.config.internal.persistence.ParameterMapper;
import com.sma.admin.config.internal.persistence.WriteLockMapper;

/** Uses the actual host interceptor and actual mapper SQL against the retained, task-only Config V7 schema. */
@EnabledIfEnvironmentVariable(named="SMA_P10_PARAMETER_UPGRADE_DB",matches=".+")
class ParameterLayerLockMysqlTests {
    private static PooledDataSource source;
    private static SqlSessionFactory factory;
    @BeforeAll static void setup() throws Exception {
        String url=System.getenv("SMA_P10_PARAMETER_UPGRADE_DB");
        if(!url.startsWith("jdbc:mysql://") || !URI.create(url.substring(5)).getPath().matches("/sma_test_p10_parameter_upgrade_[a-z0-9_]+")) throw new IllegalArgumentException("Dedicated Config V7 schema required");
        String user=System.getenv().getOrDefault("SMA_TEST_DB_USERNAME","springboot_modulith_admin"),password=System.getenv().getOrDefault("SMA_TEST_DB_PASSWORD","sma_dev");
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {
            try(var rows=sql.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='config_write_lock' AND column_name='parameter_revision'")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).as("run the V6 to V7 upgrade first").isEqualTo(1);
            }
            for(long tenant:new long[]{8201,8202,8203}) {
                sql.executeUpdate("INSERT INTO config_write_lock(id,tenant_id,created_at,created_by,updated_at,updated_by,deleted) VALUES ("+tenant+","+tenant+",UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0) ON DUPLICATE KEY UPDATE id=id");
                sql.executeUpdate("INSERT INTO config_parameter(id,tenant_id,param_key,group_key,name,value_type,param_value,created_at,created_by,updated_at,updated_by,deleted) VALUES ("+(92000+tenant)+","+tenant+",'config.lock.fixture','basic','Lock fixture','STRING','initial',UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0) ON DUPLICATE KEY UPDATE id=id");
            }
        }
        source=new PooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);source.setPoolMaximumActiveConnections(4);
        var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("parameter-lock-fixture",new JdbcTransactionFactory(),source));
        config.addMapper(ParameterMapper.class);config.addMapper(WriteLockMapper.class);
        config.addInterceptor(new PersistenceConfiguration().mybatisPlusInterceptor());
        factory=new MybatisSqlSessionFactoryBuilder().build(config);
    }
    @AfterAll static void close() { if(source!=null)source.forceCloseAll(); }

    @Test void oneTenantReadLocksDoNotBlockAnotherTenantAndCannotUpdateItsRow() {
        TenantScope.within(8201,()->{
            try(var first=factory.openSession(false);var pool=Executors.newSingleThreadExecutor()) {
                first.getMapper(WriteLockMapper.class).parameterRevision(8201);
                assertThat(first.getMapper(ParameterMapper.class).current("config.lock.fixture").getTenantId()).isEqualTo(8201);
                var other=pool.submit(()->TenantScope.within(8202,()->{
                    try(var second=factory.openSession(false)) {
                        second.getMapper(WriteLockMapper.class).lock(8202);
                        assertThat(second.getMapper(ParameterMapper.class).writeValue(100202,"other",true,0)).isEqualTo(1);
                        assertThat(second.getMapper(WriteLockMapper.class).advanceParameters(8202,0)).isEqualTo(1);
                        second.commit();return true;
                    }
                }));
                try { assertThat(other.get(10,TimeUnit.SECONDS)).isTrue(); }
                catch(Exception failure) { first.rollback();throw new IllegalStateException("Another tenant was blocked by read locks",failure); }
                first.rollback();
                assertThat(first.getMapper(ParameterMapper.class).writeValue(100202,"cross-tenant",true,0)).isZero();
                assertThat(first.getMapper(WriteLockMapper.class).parameterRevision(8202)).isNull();
                first.rollback();
            }
            return null;
        });
    }

    @Test void currentReadBypassesOldTransactionSnapshotAndMyBatisLocalCache() {
        TenantScope.within(8203,()->{
            try(var first=factory.openSession(false);var second=factory.openSession(false)) {
                var old=first.getMapper(ParameterMapper.class).selectById(100203);
                String updated=old.getParamValue().equals("next")?"initial":"next";
                second.getMapper(WriteLockMapper.class).lock(8203);
                second.getMapper(ParameterMapper.class).writeValue(100203,updated,true,0);
                second.getMapper(WriteLockMapper.class).advanceParameters(8203,0);second.commit();
                assertThat(first.getMapper(ParameterMapper.class).current("config.lock.fixture").getParamValue()).isEqualTo(updated);
                assertThat(first.getMapper(WriteLockMapper.class).parameterRevision(8203)).isPositive();first.rollback();
            }
            return null;
        });
    }
}
