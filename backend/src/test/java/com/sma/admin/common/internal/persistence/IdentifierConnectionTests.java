package com.sma.admin.common.internal.persistence;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.zaxxer.hikari.HikariDataSource;

class IdentifierConnectionTests {
    @Test void catalogOrInitializationOverridesCannotSilentlySendAllocationToAnotherDatabase(){
        for(int variant=0;variant<3;variant++)try(var pool=new HikariDataSource()){
            pool.setJdbcUrl("jdbc:mysql://private-host/original");
            if(variant==0)pool.setCatalog("private-catalog");
            if(variant==1)pool.setSchema("private-schema");
            if(variant==2)pool.setConnectionInitSql("USE private_schema");
            assertThatThrownBy(()->new DatabaseIdentifierGenerator(pool)).isInstanceOf(IllegalStateException.class).hasNoCause().hasMessageNotContaining("private");
        }
    }
    @Test void unboundedOrAutomaticRetryConnectionsAreRejectedWithoutExposingConnectionDetails(){
        for(String option:new String[]{"connectTimeout=0","connectTimeout=30000","socketTimeout=0","autoReconnect=true","useConfigs=maxPerformance"}){
            try(var pool=new HikariDataSource()){
                pool.setJdbcUrl("jdbc:mysql://private-host/db?"+option);pool.setUsername("private-user");pool.setPassword("private-password");
                assertThatThrownBy(()->new DatabaseIdentifierGenerator(pool)).isInstanceOf(IllegalStateException.class).hasNoCause()
                        .hasMessageNotContaining("private-host").hasMessageNotContaining("private-user").hasMessageNotContaining("private-password");
            }
        }
    }
}
