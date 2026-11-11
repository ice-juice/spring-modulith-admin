package com.sma.admin.message.internal.persistence;
import java.time.Instant;
import org.apache.ibatis.annotations.*;
@Mapper
public interface SocketTicketMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<SocketTicketEntity> {
    @Select("SELECT UTC_TIMESTAMP(3)") Instant now();
    @Select("SELECT * FROM message_socket_ticket WHERE portal=#{portal} AND token_hash=#{hash} AND expires_at>UTC_TIMESTAMP(3) AND deleted=0")
    SocketTicketEntity usable(String portal,String hash);
    /** Physical deletion is the atomic single-consumption transition; tenant interceptor remains enabled. */
    @Delete("DELETE FROM message_socket_ticket WHERE id=#{id} AND portal=#{portal} AND token_hash=#{hash} AND expires_at>UTC_TIMESTAMP(3) AND deleted=0")
    int consume(long id,String portal,String hash);
    @Delete("DELETE FROM message_socket_ticket WHERE portal=#{portal} AND session_id=#{session}")
    int removeSession(String portal,long session);
    @Delete("DELETE FROM message_socket_ticket WHERE expires_at<=UTC_TIMESTAMP(3) LIMIT 200")
    int removeExpired();
}
