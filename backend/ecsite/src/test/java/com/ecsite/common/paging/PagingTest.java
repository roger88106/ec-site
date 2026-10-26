package com.ecsite.common.paging;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ページングの単体テスト（API の規約）
 */
class PagingTest {

    @Nested
    @DisplayName("ページの指定")
    class PageRequestTest {

        @Test
        @DisplayName("指定がなければ 1 ページ目、20 件")
        void defaults() {
            var request = PageRequest.of(null, null);

            assertThat(request.page()).isEqualTo(1);
            assertThat(request.size()).isEqualTo(20);
        }

        @Test
        @DisplayName("ページは 1 始まり。読み飛ばす件数は (page - 1) × size")
        void offsetFromOneBasedPage() {
            assertThat(PageRequest.of(1, 20).offset()).isZero();
            assertThat(PageRequest.of(3, 20).offset()).isEqualTo(40);
        }

        @Test
        @DisplayName("件数は最大 100")
        void sizeUpTo100() {
            assertThat(PageRequest.of(1, 100).size()).isEqualTo(100);
        }

        @ParameterizedTest(name = "page={0}, size={1}")
        @CsvSource({"0, 20", "-1, 20", "1, 0", "1, 101"})
        @DisplayName("範囲外のページ・件数は拒否される")
        void rejectsOutOfRange(int page, int size) {
            assertThatThrownBy(() -> PageRequest.of(page, size))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.getErrorDetail().errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST.getCode()));
        }
    }

    @Nested
    @DisplayName("ページの結果")
    class PageResultTest {

        @ParameterizedTest(name = "総件数 {0} 件、{1} 件ずつ → {2} ページ")
        @CsvSource({"0, 20, 0", "1, 20, 1", "20, 20, 1", "21, 20, 2", "100, 20, 5", "101, 20, 6"})
        @DisplayName("総件数から総ページ数を計算する")
        void totalPages(long totalItems, int size, int expected) {
            var result = PageResult.of(List.of(), PageRequest.of(1, size), totalItems);

            assertThat(result.totalPages()).isEqualTo(expected);
        }

        @Test
        @DisplayName("中身を変換しても、ページの情報は変わらない")
        void mapKeepsPageInfo() {
            var result = PageResult.of(List.of(1, 2), PageRequest.of(2, 2), 5);

            var mapped = result.map(String::valueOf);

            assertThat(mapped.items()).containsExactly("1", "2");
            assertThat(mapped.page()).isEqualTo(2);
            assertThat(mapped.size()).isEqualTo(2);
            assertThat(mapped.totalItems()).isEqualTo(5);
            assertThat(mapped.totalPages()).isEqualTo(3);
        }
    }
}
