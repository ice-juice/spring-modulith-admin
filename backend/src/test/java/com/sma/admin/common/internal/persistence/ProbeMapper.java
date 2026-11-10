package com.sma.admin.common.internal.persistence;

import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
@com.sma.admin.common.security.DataScoped(table = "common_test_record")
public interface ProbeMapper extends BaseMapper<ProbeEntity> {
    @org.apache.ibatis.annotations.Select("SELECT r.name FROM common_test_record r WHERE r.deleted=0 AND r.name != #{name} ORDER BY r.id")
    java.util.List<String> namesExcept(String name);
    @org.apache.ibatis.annotations.Update("UPDATE common_test_record SET name='unsafe'")
    int updateAllUnsafely();
}
