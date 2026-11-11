package com.sma.admin.tenant.internal.persistence;

@org.apache.ibatis.annotations.Mapper
public interface TenantRecordMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<TenantRecordEntity> {
    /** InnoDB current read, including within an older REPEATABLE_READ transaction; bypass both MyBatis caches. */
    @org.apache.ibatis.annotations.Select("SELECT id,status,security_generation FROM tenant_tenant WHERE id=#{id} AND deleted=0 FOR SHARE")
    @org.apache.ibatis.annotations.Options(useCache=false,flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    TenantRecordEntity currentSecurityState(long id);
}
