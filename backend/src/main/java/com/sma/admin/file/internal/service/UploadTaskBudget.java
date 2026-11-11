package com.sma.admin.file.internal.service;

import java.util.Objects;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sma.admin.common.BizException;
import com.sma.admin.common.security.ActorContext;
import com.sma.admin.file.FileErrorCode;
import com.sma.admin.file.internal.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Transactional(propagation=Propagation.MANDATORY)
public class UploadTaskBudget {
    private final TaskBudgetMapper budgets;private final UploadTaskProperties settings;private final FileClock clock;
    public UploadTaskBudget(TaskBudgetMapper budgets,UploadTaskProperties settings,FileClock clock){this.budgets=budgets;this.settings=settings;this.clock=clock;}
    TaskBudgetEntity lock(){long tenant=ActorContext.tenantId();budgets.ensure(IdWorker.getId(),tenant,clock.now());var row=budgets.selectOne(new LambdaQueryWrapper<TaskBudgetEntity>().last("FOR UPDATE"));if(row==null)throw new IllegalStateException("Upload budget missing");return row;}
    TaskBudgetEntity existing(){ActorContext.tenantId();return budgets.selectOne(new LambdaQueryWrapper<TaskBudgetEntity>().last("FOR UPDATE"));}
    void reserve(TaskBudgetEntity row,long bytes){
        long minute=clock.now().getEpochSecond()/60,day=clock.now().getEpochSecond()/86400;
        int minuteStarts=Objects.equals(row.getMinuteSlot(),minute)?row.getMinuteStarts():0,dayStarts=Objects.equals(row.getDaySlot(),day)?row.getDayStarts():0;
        if(bytes<1 || bytes>settings.maximumReservedBytes()-row.getReservedBytes() || row.getActiveTasks()>=settings.maximumActive() || row.getRetainedTasks()>=settings.maximumRetained()
                || minuteStarts>=settings.perMinute() || dayStarts>=settings.perDay())throw new BizException(FileErrorCode.UPLOAD_LIMIT);
        row.setReservedBytes(Math.addExact(row.getReservedBytes(),bytes));row.setActiveTasks(row.getActiveTasks()+1);row.setRetainedTasks(row.getRetainedTasks()+1);
        row.setMinuteSlot(minute);row.setMinuteStarts(minuteStarts+1);row.setDaySlot(day);row.setDayStarts(dayStarts+1);budgets.updateById(row);
    }
    void release(TaskBudgetEntity row,long bytes){if(bytes<0 || row.getReservedBytes()<bytes || row.getActiveTasks()<1)throw new IllegalStateException("Invalid upload reservation release");
        row.setReservedBytes(row.getReservedBytes()-bytes);row.setActiveTasks(row.getActiveTasks()-1);budgets.updateById(row);}
}
