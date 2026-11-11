package com.sma.admin.workspace.internal.persistence;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
@Mapper
public interface ShelfMapper extends BaseMapper<ShelfEntity> {
 @Insert("""
 INSERT INTO workspace_shelf(id,tenant_id,user_id,portal,display_mode,version,created_at,created_by,updated_at,updated_by,deleted)
 VALUES(#{id},#{tenant},#{user},#{portal},'drawer',0,UTC_TIMESTAMP(3),#{user},UTC_TIMESTAMP(3),#{user},0)
 ON DUPLICATE KEY UPDATE id=id
 """)
 int lock(long id,long tenant,long user,String portal);
}
