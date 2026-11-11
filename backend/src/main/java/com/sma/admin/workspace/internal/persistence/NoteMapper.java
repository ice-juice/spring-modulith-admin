package com.sma.admin.workspace.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface NoteMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<NoteEntity> {
 @org.apache.ibatis.annotations.Delete("""
 DELETE FROM workspace_note WHERE user_id=#{user} AND portal=#{portal}
 AND status='trash' AND trashed_at < #{cutoff}
 """)
 int purgeExpired(long user,String portal,java.time.Instant cutoff);
}
