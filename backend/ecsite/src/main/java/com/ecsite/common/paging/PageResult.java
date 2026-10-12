package com.ecsite.common.paging;

import java.util.List;
import java.util.function.Function;

/**
 * ページの結果（constitution 9）。<br />
 * レスポンスの {@code { items, page, size, totalItems, totalPages }} の元になる。
 * API ごとのレスポンスの型（OpenAPI の生成モデル）への変換は、presentation 層で行う。
 *
 * @param items      このページの要素
 * @param page       ページ番号（1 始まり）
 * @param size       1 ページの件数
 * @param totalItems 総件数
 * @param <T>        要素の型
 */
public record PageResult<T>(List<T> items, int page, int size, long totalItems) {

    /**
     * コンストラクタ。
     */
    public PageResult {
        items = List.copyOf(items);
    }

    /**
     * ページの結果を作る。
     *
     * @param items      このページの要素
     * @param request    ページの指定
     * @param totalItems 総件数
     * @param <T>        要素の型
     * @return ページの結果
     */
    public static <T> PageResult<T> of(List<T> items, PageRequest request, long totalItems) {
        return new PageResult<>(items, request.page(), request.size(), totalItems);
    }

    /**
     * 総ページ数。0 件なら 0。
     *
     * @return 総ページ数
     */
    public int totalPages() {
        return Math.toIntExact((totalItems + size - 1) / size);
    }

    /**
     * 要素を変換する。ページの情報はそのまま。
     *
     * @param mapper 変換
     * @param <R>    変換後の型
     * @return 変換したページの結果
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(items.stream().<R>map(mapper).toList(), page, size, totalItems);
    }
}
