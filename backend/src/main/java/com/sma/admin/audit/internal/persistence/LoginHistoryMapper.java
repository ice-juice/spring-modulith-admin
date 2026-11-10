package com.sma.admin.audit.internal.persistence;
/** Event ingestion compares prior successful locations independently of the request's data grant. */
@org.apache.ibatis.annotations.Mapper
public interface LoginHistoryMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<LoginLogEntity> {}
