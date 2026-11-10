package com.sma.admin.common;

import java.util.List;
import java.util.function.Function;
import com.baomidou.mybatisplus.core.metadata.IPage;

public record PageResult<T>(List<T> items, long total, long page, long size) {
    public PageResult { items = List.copyOf(items); }
    public static <S,T> PageResult<T> of(IPage<S> page, Function<S,T> convert) {
        return new PageResult<>(page.getRecords().stream().map(convert).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
