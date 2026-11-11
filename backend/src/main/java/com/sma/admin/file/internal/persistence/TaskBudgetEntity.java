package com.sma.admin.file.internal.persistence;
@lombok.Getter @lombok.Setter @com.baomidou.mybatisplus.annotation.TableName("file_task_budget")
public class TaskBudgetEntity extends com.sma.admin.common.persistence.TenantEntity {
    private Integer activeTasks;private Integer retainedTasks;private Long reservedBytes;
    private Long minuteSlot;private Integer minuteStarts;private Long daySlot;private Integer dayStarts;
}
