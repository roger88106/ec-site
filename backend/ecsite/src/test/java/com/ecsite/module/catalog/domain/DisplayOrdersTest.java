package com.ecsite.module.catalog.domain;

import com.ecsite.common.error.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 表示順の振り直しの単体テスト（spec 005 DC-40）
 */
class DisplayOrdersTest {

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

    @Nested
    @DisplayName("並べ替え")
    class Assign {

        @Test
        @DisplayName("送られた順に 1 から表示順を振る")
        void assignsInOrder() {
            Map<UUID, Integer> orders = DisplayOrders.assign(List.of(a, b, c), List.of(c, a, b), CommonErrorCode.INVALID_REQUEST);

            assertThat(orders).containsExactlyInAnyOrderEntriesOf(Map.of(c, 1, a, 2, b, 3));
        }

        @Test
        @DisplayName("今の要素にない ID、漏れ、重複があれば、業務のエラー")
        void rejectsMismatch() {
            var current = List.of(a, b, c);
            assertBusinessError(() -> DisplayOrders.assign(current, List.of(a, b, UUID.randomUUID()),
                    CommonErrorCode.INVALID_REQUEST), CommonErrorCode.INVALID_REQUEST);
            assertBusinessError(() -> DisplayOrders.assign(current, List.of(a, b),
                    CommonErrorCode.INVALID_REQUEST), CommonErrorCode.INVALID_REQUEST);
            assertBusinessError(() -> DisplayOrders.assign(current, List.of(a, b, b),
                    CommonErrorCode.INVALID_REQUEST), CommonErrorCode.INVALID_REQUEST);
        }
    }

    @Nested
    @DisplayName("末尾への追加")
    class Next {

        @Test
        @DisplayName("今の表示順の最大の次。要素がなければ 1")
        void nextAfterMax() {
            assertThat(DisplayOrders.next(IntStream.of(1, 3, 2))).isEqualTo(4);
            assertThat(DisplayOrders.next(IntStream.empty())).isEqualTo(1);
        }
    }
}
