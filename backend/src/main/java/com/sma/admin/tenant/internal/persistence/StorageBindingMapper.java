package com.sma.admin.tenant.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StorageBindingMapper extends BaseMapper<StorageBindingEntity> {
    @Select("SELECT * FROM tenant_storage_binding WHERE id=#{id} AND deleted=0 FOR UPDATE")
    StorageBindingEntity lockById(long id);
}
