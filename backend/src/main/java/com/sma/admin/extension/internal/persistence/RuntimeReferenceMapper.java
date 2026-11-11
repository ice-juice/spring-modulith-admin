package com.sma.admin.extension.internal.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface RuntimeReferenceMapper extends BaseMapper<RuntimeReferenceEntity> {
    @Insert("INSERT INTO extension_runtime_reference(id,deployment_id,bundle_id,artifact_hash,reference_count) VALUES(#{id},#{deployment},#{bundle},#{hash},1) ON DUPLICATE KEY UPDATE reference_count=reference_count+1")
    void retain(@Param("id")String id,@Param("deployment")String deployment,@Param("bundle")String bundle,@Param("hash")String hash);
}
