package com.sma.admin.common;

import java.util.Map;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/** The SQL sort column must come from a server-defined allowlist. */
public record PageQuery(Integer page, Integer size, String sort, String direction) {
    public <T> Page<T> toPage(Map<String, String> allowedSorts) {
        int number = page == null ? 1 : page;
        int limit = size == null ? 20 : size;
        if (number < 1 || limit < 1 || limit > 200 || (direction != null && !direction.equals("asc") && !direction.equals("desc"))) {
            throw new BizException(CommonErrorCode.BAD_REQUEST);
        }
        Page<T> result = new Page<>(number, limit);
        if (sort != null && !sort.isBlank()) {
            String column = allowedSorts.get(sort);
            if (column == null || !column.matches("[a-z_]+")) { throw new BizException(CommonErrorCode.BAD_REQUEST); }
            result.addOrder("desc".equals(direction) ? OrderItem.desc(column) : OrderItem.asc(column));
        }
        return result;
    }
}
