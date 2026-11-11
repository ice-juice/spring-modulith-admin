package com.sma.admin.file.internal.persistence;
@org.apache.ibatis.annotations.Mapper
public interface TaskBudgetMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<TaskBudgetEntity> {
    @org.apache.ibatis.annotations.Insert("INSERT INTO file_task_budget(id,tenant_id,created_at,created_by,updated_at,updated_by,deleted) VALUES(#{id},#{tenant},#{now},0,#{now},0,0) ON DUPLICATE KEY UPDATE id=file_task_budget.id")
    int ensure(long id,long tenant,java.time.Instant now);
}
