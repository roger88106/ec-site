package com.ecsite.common.paging;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import org.jspecify.annotations.Nullable;

/**
 * ページの指定（API の規約）。<br />
 * ページは 1 始まり、件数は既定 20・最大 100。MVP はオフセット方式。
 *
 * @param page ページ番号（1 始まり）
 * @param size 1 ページの件数
 */
public record PageRequest(int page, int size) {

    /**
     * 既定のページ番号
     */
    public static final int DEFAULT_PAGE = 1;

    /**
     * 既定の件数
     */
    public static final int DEFAULT_SIZE = 20;

    /**
     * 件数の上限
     */
    public static final int MAX_SIZE = 100;

    /**
     * コンストラクタ。<br />
     * 範囲は API の入力検証でも確認するが、ここでも守る。
     *
     * @param page ページ番号（1 始まり）
     * @param size 1 ページの件数
     */
    public PageRequest {
        // ページ番号と件数は 1 以上、件数は上限まで
        if (page < 1 || size < 1 || size > MAX_SIZE) {
            throw BusinessException.of(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * リクエストのパラメータから作る。指定がなければ既定値にする。
     *
     * @param page ページ番号（1 始まり）。null は 1
     * @param size 1 ページの件数。null は 20
     * @return ページの指定
     */
    public static PageRequest of(@Nullable Integer page, @Nullable Integer size) {
        return new PageRequest(
                page != null ? page : DEFAULT_PAGE,
                size != null ? size : DEFAULT_SIZE);
    }

    /**
     * 読み飛ばす件数
     *
     * @return (page - 1) × size
     */
    public long offset() {
        return (long) (page - 1) * size;
    }
}
