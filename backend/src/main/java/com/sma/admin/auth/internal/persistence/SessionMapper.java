package com.sma.admin.auth.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface SessionMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<SessionEntity> {
    @org.apache.ibatis.annotations.Select("SELECT UTC_TIMESTAMP(3)") java.time.Instant now();
    @org.apache.ibatis.annotations.Update("""
        UPDATE auth_session SET locked=1,updated_at=UTC_TIMESTAMP(3),updated_by=#{user}
        WHERE id=#{id} AND user_id=#{user} AND portal=#{portal} AND deleted=0 AND locked=0
          AND expires_at>UTC_TIMESTAMP(3) AND last_seen_at=#{observed} AND last_seen_at<#{cutoff}
        """)
    int autoLock(long id,long user,String portal,java.time.Instant observed,java.time.Instant cutoff);
    @org.apache.ibatis.annotations.Update("""
        UPDATE auth_session SET last_seen_at=GREATEST(last_seen_at,UTC_TIMESTAMP(3)),updated_at=UTC_TIMESTAMP(3),updated_by=#{user}
        WHERE id=#{id} AND user_id=#{user} AND portal=#{portal} AND deleted=0 AND locked=0 AND expires_at>UTC_TIMESTAMP(3)
        """)
    int touch(long id,long user,String portal);
    @org.apache.ibatis.annotations.Update("""
        UPDATE auth_session SET locked=1,updated_at=UTC_TIMESTAMP(3),updated_by=#{user}
        WHERE id=#{id} AND user_id=#{user} AND portal=#{portal} AND deleted=0 AND expires_at>UTC_TIMESTAMP(3)
        """)
    int lockSession(long id,long user,String portal);
    @org.apache.ibatis.annotations.Update("""
        UPDATE auth_session SET locked=0,last_seen_at=GREATEST(last_seen_at,UTC_TIMESTAMP(3)),updated_at=UTC_TIMESTAMP(3),updated_by=#{user}
        WHERE id=#{id} AND user_id=#{user} AND portal=#{portal} AND deleted=0 AND expires_at>UTC_TIMESTAMP(3)
        """)
    int unlockSession(long id,long user,String portal);
}
