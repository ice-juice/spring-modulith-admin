package com.sma.admin.message.internal.persistence;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sma.admin.common.security.DataScoped;
@Mapper @DataScoped(table="message_notice")
public interface NoticeRecycleMapper extends BaseMapper<NoticeEntity>{
 @Select("SELECT * FROM message_notice WHERE portal=#{portal} AND deleted=1 ORDER BY id DESC")
 Page<NoticeEntity> recycled(Page<NoticeEntity> page,String portal);
 @Update("UPDATE message_notice SET deleted=0,updated_at=#{now},updated_by=#{actor} WHERE id=#{id} AND portal=#{portal} AND deleted=1")
 int restore(long id,String portal,java.time.Instant now,long actor);
}
