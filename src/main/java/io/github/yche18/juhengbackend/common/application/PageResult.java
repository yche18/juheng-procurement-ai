package io.github.yche18.juhengbackend.common.application;

import java.util.List;

/**
 * 应用层与协议无关的分页结果。
 *
 * @param content 当前页内容
 * @param page 从零开始的页码
 * @param size 请求的单页大小
 * @param totalElements 符合条件的总记录数
 * @param totalPages 总页数
 * @param <T> 页内元素类型
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        long totalPages)
{

    /**
     * 校验分页元数据并复制内容，防止调用方修改已经生成的查询结果。
     */
    public PageResult
    {
        content = List.copyOf(content);
        if (page < 0 || size <= 0 || totalElements < 0 || totalPages < 0)
        {
            throw new IllegalArgumentException("Page metadata must not be negative");
        }
    }
}
