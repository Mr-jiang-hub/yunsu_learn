package com.yunshu.zhixue.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 通用分页参数：page 从 1 开始，size 默认 20、上限 100（api-rules 第 7 章）。
 */
public class PageQuery {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    @Min(value = 1, message = "页码必须大于等于 1")
    private int page = 1;

    @Min(value = 1, message = "每页条数必须大于等于 1")
    @Max(value = MAX_SIZE, message = "每页条数不能超过 " + MAX_SIZE)
    private int size = DEFAULT_SIZE;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long offset() {
        return (long) (page - 1) * size;
    }
}
