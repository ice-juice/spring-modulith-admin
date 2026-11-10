package com.sma.admin.iam.internal.persistence;
/** Authentication reads are tenant-filtered but intentionally independent of a user's data scope. */
@org.apache.ibatis.annotations.Mapper
public interface AccountMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<UserEntity> {}
