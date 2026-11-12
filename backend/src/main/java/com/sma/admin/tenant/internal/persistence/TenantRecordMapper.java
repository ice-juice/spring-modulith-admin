package com.sma.admin.tenant.internal.persistence;

@org.apache.ibatis.annotations.Mapper
public interface TenantRecordMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<TenantRecordEntity> {
    /** Serialize first-start bootstrap without overwriting operator-owned values or reviving a deleted row. */
    @org.apache.ibatis.annotations.Insert("""
        INSERT INTO tenant_tenant(id,code,name,status,security_generation,created_at,created_by,updated_at,updated_by,deleted)
        VALUES(#{id},'platform','Platform','ACTIVE',1,UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
        ON DUPLICATE KEY UPDATE id=id
        """)
    int ensureBootstrap(long id);
    /** InnoDB current read, including within an older REPEATABLE_READ transaction; bypass both MyBatis caches. */
    @org.apache.ibatis.annotations.Select("SELECT id,status,security_generation FROM tenant_tenant WHERE id=#{id} AND deleted=0 FOR SHARE")
    @org.apache.ibatis.annotations.Options(useCache=false,flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    TenantRecordEntity currentSecurityState(long id);
}
