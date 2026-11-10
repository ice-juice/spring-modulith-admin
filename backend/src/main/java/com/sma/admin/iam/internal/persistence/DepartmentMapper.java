package com.sma.admin.iam.internal.persistence;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sma.admin.common.security.DataScoped;

@Mapper
@DataScoped(table = "iam_department", department = "id", owner = "")
public interface DepartmentMapper extends BaseMapper<DepartmentEntity> {}
