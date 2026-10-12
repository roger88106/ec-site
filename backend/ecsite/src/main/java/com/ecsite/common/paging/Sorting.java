package com.ecsite.common.paging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jooq.Field;
import org.jooq.SortField;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一覧の並び順の部品（jOOQ）。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Sorting {

    /**
     * 並びの最後に ID を加える。<br />
     * 並びの基準が同じ値の行があると、オフセット方式のページングでページをまたいで重複・欠落が出るため、
     * ID で順序を確定させる（constitution 9）。MVP の暫定のやり方で、検索エンジンの導入時に見直す。
     *
     * @param sortFields 並びの基準
     * @param id         ID の列
     * @return ID を加えた並び
     */
    public static List<SortField<?>> withIdTiebreaker(List<? extends SortField<?>> sortFields, Field<UUID> id) {
        var result = new ArrayList<SortField<?>>(sortFields);
        result.add(id.asc());
        return List.copyOf(result);
    }
}
