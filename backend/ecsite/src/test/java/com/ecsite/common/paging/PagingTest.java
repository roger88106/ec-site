package com.ecsite.common.paging;

import com.ecsite.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ページングの単体テスト
 */
class PagingTest {

    // ============================== 定数 ==============================

    /**
     * 範囲の中のページ
     */
    private static final int VALID_PAGE = 1;

    /**
     * 範囲の中の件数（指定がないときの件数）
     */
    private static final int VALID_SIZE = 20;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("ページの指定")
    class PageRequestTest {

        @Test
        @DisplayName("指定がなければ 1 ページ目、20 件")
        void defaults() {
            // 操作：ページも件数も指定しない
            var request = PageRequest.of(null, null);

            // 確認：1 ページ目、20 件
            assertThat(request.page()).isEqualTo(1);
            assertThat(request.size()).isEqualTo(20);
        }

        @ParameterizedTest(name = "「{0}」")
        @CsvSource({"1, 0", "3, 40"})
        @DisplayName("ページは 1 始まり。読み飛ばす件数は (page - 1) × size")
        void offsetFromOneBasedPage(int page, int offset) {
            // 操作と確認：20 件ずつの読み飛ばす件数
            assertThat(PageRequest.of(page, VALID_SIZE).offset()).isEqualTo(offset);
        }

        @Test
        @DisplayName("件数は最小 1")
        void sizeFrom1() {
            // 操作と確認：1 件は指定できる
            assertThat(PageRequest.of(VALID_PAGE, 1).size()).isEqualTo(1);
        }

        @Test
        @DisplayName("件数は最大 100")
        void sizeUpTo100() {
            // 操作と確認：100 件は指定できる
            assertThat(PageRequest.of(VALID_PAGE, 100).size()).isEqualTo(100);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, -1})
        @DisplayName("1 より小さいページは拒否される")
        void rejectsOutOfRangePage(int page) {
            // 操作と確認：業務のエラー INVALID_REQUEST
            assertBusinessError(() -> PageRequest.of(page, VALID_SIZE), CommonErrorCode.INVALID_REQUEST);
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, 101})
        @DisplayName("1〜100 の外の件数は拒否される")
        void rejectsOutOfRangeSize(int size) {
            // 操作と確認：業務のエラー INVALID_REQUEST
            assertBusinessError(() -> PageRequest.of(VALID_PAGE, size), CommonErrorCode.INVALID_REQUEST);
        }
    }

    @Nested
    @DisplayName("ページの結果")
    class PageResultTest {

        @ParameterizedTest(name = "総件数 {0} 件、{1} 件ずつ → {2} ページ")
        @CsvSource({"0, 20, 0", "1, 20, 1", "20, 20, 1", "21, 20, 2", "100, 20, 5", "101, 20, 6"})
        @DisplayName("総件数から総ページ数を計算する")
        void totalPages(long totalItems, int size, int expected) {
            // 操作：総件数を渡して結果を作る
            var result = PageResult.of(List.of(), PageRequest.of(1, size), totalItems);

            // 確認：総ページ数
            assertThat(result.totalPages()).isEqualTo(expected);
        }

        @Test
        @DisplayName("中身を変換しても、ページの情報は変わらない")
        void mapKeepsPageInfo() {
            // 準備：2 ページ目、2 件ずつ、総件数 5 件の結果
            var result = PageResult.of(List.of(1, 2), PageRequest.of(2, 2), 5);

            // 操作：中身を文字に変換する
            var mapped = result.map(String::valueOf);

            // 確認：中身だけが変わる
            assertMappedKeepingPageInfo(mapped);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 2 ページ目、2 件ずつ、総件数 5 件の結果を文字に変換した結果を確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>中身が文字に変わっている</li>
     *   <li>ページ番号が 2 のまま</li>
     *   <li>1 ページの件数が 2 のまま</li>
     *   <li>総件数が 5 のまま</li>
     *   <li>総ページ数が 3 のまま</li>
     * </ul>
     *
     * @param mapped 変換した結果
     */
    private static void assertMappedKeepingPageInfo(PageResult<String> mapped) {
        assertThat(mapped.items()).containsExactly("1", "2");
        assertThat(mapped.page()).isEqualTo(2);
        assertThat(mapped.size()).isEqualTo(2);
        assertThat(mapped.totalItems()).isEqualTo(5);
        assertThat(mapped.totalPages()).isEqualTo(3);
    }
}
