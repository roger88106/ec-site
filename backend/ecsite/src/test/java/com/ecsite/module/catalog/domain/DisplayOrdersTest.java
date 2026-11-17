package com.ecsite.module.catalog.domain;

import com.ecsite.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 表示順の振り直しの単体テスト
 */
class DisplayOrdersTest {

    // ============================== テストのデータ ==============================

    /**
     * 今の要素の ID（1 つ目）
     */
    private final UUID a = UUID.randomUUID();

    /**
     * 今の要素の ID（2 つ目）
     */
    private final UUID b = UUID.randomUUID();

    /**
     * 今の要素の ID（3 つ目）
     */
    private final UUID c = UUID.randomUUID();

    // ============================== テスト ==============================

    @Nested
    @DisplayName("並べ替え")
    class Assign {

        @Test
        @DisplayName("送られた順に 1 から表示順を振る")
        void assignsInOrder() {
            // 操作：A、B、C を C、A、B の順に並べ替える
            Map<UUID, Integer> orders = DisplayOrders.assign(List.of(a, b, c), List.of(c, a, b), CommonErrorCode.INVALID_REQUEST);

            // 確認：C が 1、A が 2、B が 3
            assertThat(orders).containsExactlyInAnyOrderEntriesOf(Map.of(c, 1, a, 2, b, 3));
        }

        @Test
        @DisplayName("今の要素にない ID があれば、指定した業務のエラー")
        void rejectsUnknownId() {
            // 操作と確認：知らない ID を混ぜると、指定した業務のエラーで拒否される
            assertBusinessError(() -> DisplayOrders.assign(List.of(a, b, c), List.of(a, b, UUID.randomUUID()),
                    CommonErrorCode.INVALID_REQUEST), CommonErrorCode.INVALID_REQUEST);
        }

        @Test
        @DisplayName("今の要素に漏れがあれば、指定した業務のエラー")
        void rejectsMissingId() {
            // 操作と確認：C を送らないと、指定した業務のエラーで拒否される
            assertBusinessError(() -> DisplayOrders.assign(List.of(a, b, c), List.of(a, b),
                    CommonErrorCode.INVALID_REQUEST), CommonErrorCode.INVALID_REQUEST);
        }

        @Test
        @DisplayName("同じ ID が重複していれば、指定した業務のエラー")
        void rejectsDuplicatedId() {
            // 操作と確認：B を 2 回送ると、指定した業務のエラーで拒否される
            assertBusinessError(() -> DisplayOrders.assign(List.of(a, b, c), List.of(a, b, b),
                    CommonErrorCode.INVALID_REQUEST), CommonErrorCode.INVALID_REQUEST);
        }
    }

    @Nested
    @DisplayName("末尾への追加")
    class Next {

        @Test
        @DisplayName("今の表示順の最大の次の値を返す")
        void nextAfterMax() {
            // 操作と確認：1、3、2 の次は 4
            assertThat(DisplayOrders.next(IntStream.of(1, 3, 2))).isEqualTo(4);
        }

        @Test
        @DisplayName("要素がなければ 1 を返す")
        void oneWhenEmpty() {
            // 操作と確認：空なら 1
            assertThat(DisplayOrders.next(IntStream.empty())).isEqualTo(1);
        }
    }
}
