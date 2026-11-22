package com.ecsite.module.catalog.domain;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.model.id.VariantId;
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
    private final VariantId a = new VariantId(UUID.randomUUID());

    /**
     * 今の要素の ID（2 つ目）
     */
    private final VariantId b = new VariantId(UUID.randomUUID());

    /**
     * 今の要素の ID（3 つ目）
     */
    private final VariantId c = new VariantId(UUID.randomUUID());

    // ============================== テスト ==============================

    @Nested
    @DisplayName("並べ替え")
    class Assign {

        @Test
        @DisplayName("指定された順に 1 から表示順を振る")
        void assignsInOrder() {
            // 操作：A、B、C を C、A、B の順に並べ替える
            Map<VariantId, Integer> orders = DisplayOrders.assign(List.of(a, b, c), List.of(c, a, b), CommonErrorCode.INVALID_REQUEST);

            // 確認：C が 1、A が 2、B が 3
            assertThat(orders).containsExactlyInAnyOrderEntriesOf(Map.of(c, 1, a, 2, b, 3));
        }

        @Test
        @DisplayName("今の要素にない ID があれば、指定した業務のエラー")
        void rejectsUnknownId() {
            // 操作と確認：知らない ID を混ぜると、指定した業務のエラーで拒否される
            assertBusinessError(() -> DisplayOrders.assign(List.of(a, b, c), List.of(a, b, new VariantId(UUID.randomUUID())),
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
    @DisplayName("要素の並べ替え")
    class Reorder {

        @Test
        @DisplayName("指定された順に、1 から新しい表示順を振った要素を返す")
        void assignsNewDisplayOrders() {
            // 準備：表示順が 5、7、9 の要素 A、B、C
            var items = List.of(new Item(a, 5), new Item(b, 7), new Item(c, 9));

            // 操作：C、A、B の順に並べ替える
            var result = DisplayOrders.reorder(items, Item::id, Item::withDisplayOrder, List.of(c, a, b),
                    CommonErrorCode.INVALID_REQUEST);

            // 確認：C が 1、A が 2、B が 3
            assertThat(result).containsExactlyInAnyOrder(new Item(c, 1), new Item(a, 2), new Item(b, 3));
        }

        @Test
        @DisplayName("今の要素と ID の集合が一致しなければ、指定した業務のエラー")
        void rejectsMismatchedIds() {
            // 準備：表示順が 1、2 の要素 A、B
            var items = List.of(new Item(a, 1), new Item(b, 2));

            // 操作と確認：A だけを指定すると、指定した業務のエラーで拒否される
            assertBusinessError(() -> DisplayOrders.reorder(items, Item::id, Item::withDisplayOrder, List.of(a),
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

    // ============================== テストの補助 ==============================

    /**
     * 表示順を持つ要素
     *
     * @param id           ID
     * @param displayOrder 表示順
     */
    private record Item(VariantId id, int displayOrder) {

        /**
         * 表示順を変えた要素を作る。
         *
         * @param newDisplayOrder 新しい表示順
         * @return 表示順を変えた要素
         */
        Item withDisplayOrder(int newDisplayOrder) {
            return new Item(id, newDisplayOrder);
        }
    }
}
