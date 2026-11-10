package com.sma.admin.iam.internal.persistence;

import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface StructureLockMapper extends BaseMapper<StructureLockEntity> {
    @Insert("""
            INSERT INTO iam_structure_lock (id,tenant_id,created_at,created_by,updated_at,updated_by,deleted)
            VALUES (#{tenant},#{tenant},UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
            ON DUPLICATE KEY UPDATE id=id
            """)
    int lock(long tenant);
    @Select("SELECT COUNT(*) FROM iam_department WHERE deleted=0 AND path LIKE CONCAT(#{path},'%')")
    long subtreeCount(String path);
    @Select("""
            SELECT d.id FROM iam_department d WHERE d.deleted=0
            AND d.path LIKE CONCAT((SELECT p.path FROM iam_department p WHERE p.id=#{id} AND p.deleted=0),'%')
            """)
    java.util.List<Long> descendantIds(long id);
}
