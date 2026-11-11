package com.sma.admin.message.internal.persistence;
import org.apache.ibatis.annotations.*;
@Mapper
public interface SocketTicketGuardMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<SocketTicketGuardEntity> {
    @Insert("""
        INSERT INTO message_socket_ticket_guard(id,tenant_id,created_at,created_by,updated_at,updated_by,deleted)
        VALUES(#{id},#{tenant},UTC_TIMESTAMP(3),0,UTC_TIMESTAMP(3),0,0)
        ON DUPLICATE KEY UPDATE id=id
        """)
    int ensure(long id,long tenant);
}
