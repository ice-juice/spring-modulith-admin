package com.sma.admin.file.internal.persistence;
import com.sma.admin.common.security.DataScoped;
@org.apache.ibatis.annotations.Mapper
@DataScoped(table="file_asset",department="dept_id",owner="created_by")
public interface FileMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<FileEntity> {}
