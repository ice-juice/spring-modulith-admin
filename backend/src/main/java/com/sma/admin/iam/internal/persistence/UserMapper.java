package com.sma.admin.iam.internal.persistence;
import com.sma.admin.common.security.DataScoped;
@org.apache.ibatis.annotations.Mapper
@DataScoped(table="iam_user",department="dept_id",owner="id")
public interface UserMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<UserEntity> {}
