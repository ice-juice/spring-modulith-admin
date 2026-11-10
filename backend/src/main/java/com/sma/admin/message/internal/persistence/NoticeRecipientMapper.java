package com.sma.admin.message.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface NoticeRecipientMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<NoticeRecipientEntity>{
 @org.apache.ibatis.annotations.Update("UPDATE message_notice_recipient SET read_at=#{now},updated_at=#{now},updated_by=#{userId} WHERE user_id=#{userId} AND deleted=0 AND read_at IS NULL AND notice_id IN (SELECT id FROM message_notice WHERE portal=#{portal} AND deleted=0)")
 int readAll(long userId,String portal,java.time.Instant now);
}
