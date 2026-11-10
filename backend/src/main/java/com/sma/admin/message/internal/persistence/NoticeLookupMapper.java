package com.sma.admin.message.internal.persistence;
/** Recipient authorization is explicit in the service; tenant filtering always applies. */
@org.apache.ibatis.annotations.Mapper
public interface NoticeLookupMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<NoticeEntity>{
 @org.apache.ibatis.annotations.Select("<script>SELECT * FROM message_notice WHERE portal=#{portal} AND deleted=0 AND id IN (SELECT notice_id FROM message_notice_recipient WHERE user_id=#{userId} AND deleted=0 <if test='unread'>AND read_at IS NULL</if>) ORDER BY priority DESC,id DESC</script>")
 com.baomidou.mybatisplus.extension.plugins.pagination.Page<NoticeEntity> inbox(com.baomidou.mybatisplus.extension.plugins.pagination.Page<NoticeEntity> page,String portal,long userId,boolean unread);
}
