package com.river.agi.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("分页结果测试")
class PageResultTest {

    @Test
    @DisplayName("of - 创建分页结果")
    void of_success() {
        PageResult<String> result = PageResult.of(List.of("a", "b"), 2, 1, 10);

        assertNotNull(result);
        assertEquals(2, result.getRecords().size());
        assertEquals(2L, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(10, result.getSize());
    }

    @Test
    @DisplayName("of - 空列表")
    void of_empty() {
        PageResult<String> result = PageResult.of(List.of(), 0, 1, 10);

        assertNotNull(result);
        assertTrue(result.getRecords().isEmpty());
        assertEquals(0L, result.getTotal());
    }

    @Test
    @DisplayName("of - null 列表")
    void of_nullList() {
        PageResult<String> result = PageResult.of(null, 0, 1, 10);

        assertNotNull(result);
        assertNull(result.getRecords());
        assertEquals(0L, result.getTotal());
    }

    @Test
    @DisplayName("setter - 设置字段")
    void setters_success() {
        PageResult<String> result = new PageResult<>();
        result.setRecords(List.of("x"));
        result.setTotal(100L);
        result.setPage(2);
        result.setSize(20);

        assertEquals(1, result.getRecords().size());
        assertEquals(100L, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(20, result.getSize());
    }

    @Test
    @DisplayName("equals 和 hashCode - Lombok 生成")
    void equalsHashCode() {
        PageResult<String> r1 = PageResult.of(List.of("a"), 1, 1, 10);
        PageResult<String> r2 = PageResult.of(List.of("a"), 1, 1, 10);

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }
}
