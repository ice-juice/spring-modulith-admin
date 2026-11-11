package com.sma.admin.message.internal.persistence;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.*;
@Mapper
public interface SocketSignalMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<SocketSignalEntity> {
    @Select("SELECT UTC_TIMESTAMP(3)") Instant now();
    @Delete("DELETE FROM message_socket_signal WHERE expires_at<=UTC_TIMESTAMP(3) LIMIT 200") int purgeExpired();
    @Insert("""
        <script>
        INSERT INTO message_socket_signal(id,tenant_id,portal,user_id,signal_type,object_id,revision,reason,expires_at,created_at,created_by,updated_at,updated_by,deleted)
        VALUES
        <foreach collection="rows" item="row" separator=",">
          (#{row.id},#{tenant},#{row.portal},#{row.userId},#{row.signalType},#{row.objectId},1,#{row.reason},#{row.expiresAt},UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
        </foreach>
        ON DUPLICATE KEY UPDATE object_id=VALUES(object_id),revision=revision+1,reason=VALUES(reason),expires_at=VALUES(expires_at),updated_at=UTC_TIMESTAMP(3),updated_by=0
        </script>
        """)
    int coalesce(@Param("tenant") long tenant,@Param("rows") List<SocketSignalEntity> rows);
}
