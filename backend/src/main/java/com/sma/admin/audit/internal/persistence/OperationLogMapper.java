package com.sma.admin.audit.internal.persistence;
import com.sma.admin.common.security.DataScoped;
@org.apache.ibatis.annotations.Mapper
@DataScoped(table="audit_operation_log",department="dept_id",owner="user_id")
public interface OperationLogMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<OperationLogEntity> {}
