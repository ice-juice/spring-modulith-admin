package com.sma.admin.file.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface FileLookupMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<FileEntity> {
    // Reconciliation evidence includes logically deleted metadata; never use this for downloads or business reads.
    @org.apache.ibatis.annotations.Select("SELECT * FROM file_asset WHERE tenant_id=#{tenant} AND id=#{id} AND portal=#{portal} AND created_by=#{owner}")
    FileEntity uploadEvidence(@org.apache.ibatis.annotations.Param("tenant") long tenant,
        @org.apache.ibatis.annotations.Param("id") long id, @org.apache.ibatis.annotations.Param("portal") String portal,
        @org.apache.ibatis.annotations.Param("owner") long owner);
}
