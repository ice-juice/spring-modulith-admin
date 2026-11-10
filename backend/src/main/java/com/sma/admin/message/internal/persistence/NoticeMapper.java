package com.sma.admin.message.internal.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sma.admin.common.security.DataScoped;
@org.apache.ibatis.annotations.Mapper @DataScoped(table="message_notice")
public interface NoticeMapper extends BaseMapper<NoticeEntity>{}
