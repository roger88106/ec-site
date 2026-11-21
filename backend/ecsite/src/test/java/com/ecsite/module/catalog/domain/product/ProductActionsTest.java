package com.ecsite.module.catalog.domain.product;

import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductActions;
import com.ecsite.module.catalog.domain.product.vo.ProductActions.DeleteBlockedReason;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 商品にできる操作（画面に返すフラグ）の単体テスト
 *
 * <p>フラグの値が、集約の規則（操作を受け付けるか、拒むか）と同じになることを確認する。
 */
class ProductActionsTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 税区分 ID
     *
     * <p>税モジュールの ID なので、存在しなくてよい。
     */
    private static final UUID TAX_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * 商品の生成
     */
    private final ProductFactory factory = new ProductFactory();

    // ============================== テスト ==============================

    @Nested
    @DisplayName("公開の状態の操作")
    class Status {

        @Test
        @DisplayName("非公開の商品は、公開と販売終了ができ、非公開にはできない")
        void unpublished() {
            // 準備：非公開の商品
            var product = productWithVariant();

            // 操作と確認：公開・非公開・販売終了の順に、できる・できない・できる
            assertThat(product.actions(NOW))
                    .extracting(ProductActions::publish, ProductActions::unpublish, ProductActions::discontinue)
                    .containsExactly(true, false, true);
        }

        @Test
        @DisplayName("公開中の商品は、非公開と販売終了ができ、公開にはできない")
        void published() {
            // 準備：公開中の商品
            var product = productWithVariant();
            product.publish(NOW);

            // 操作と確認：公開・非公開・販売終了の順に、できない・できる・できる
            assertThat(product.actions(NOW))
                    .extracting(ProductActions::publish, ProductActions::unpublish, ProductActions::discontinue)
                    .containsExactly(false, true, true);
        }

        @Test
        @DisplayName("販売終了の商品は、公開・非公開・販売終了のどれもできない")
        void discontinued() {
            // 準備：公開してから販売終了にした商品
            var product = productWithVariant();
            product.publish(NOW);
            product.discontinue(NOW);

            // 操作と確認：どれもできない
            assertThat(product.actions(NOW))
                    .extracting(ProductActions::publish, ProductActions::unpublish, ProductActions::discontinue)
                    .containsExactly(false, false, false);
        }
    }

    @Nested
    @DisplayName("削除")
    class Delete {

        @Test
        @DisplayName("AC-33、71 公開していない商品は、商品とバリアントを削除できる")
        void neverPublished() {
            // 準備：非公開の商品
            var product = productWithVariant();

            // 確認：商品とバリアントの削除のフラグが立つ
            assertThat(product.actions(NOW).delete()).isTrue();
            assertThat(product.actions(NOW).deleteVariants()).isTrue();

            // 操作と確認：集約も商品の削除を受け付ける
            assertThatCode(() -> product.delete(NOW)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-33、71 初回表示日時が未来なら、商品とバリアントを削除できる")
        void scheduled() {
            // 準備：公開開始日時が 1 秒後で、今公開した商品
            var product = productWithVariant();
            product.changePublicationPeriod(PublicationPeriod.of(NOW.plusSeconds(1), null), NOW);
            product.publish(NOW);

            // 確認：商品とバリアントの削除のフラグが立つ
            assertThat(product.actions(NOW).delete()).isTrue();
            assertThat(product.actions(NOW).deleteVariants()).isTrue();

            // 操作と確認：集約も商品の削除を受け付ける
            assertThatCode(() -> product.delete(NOW)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-33、71 初回表示日時ちょうどから、商品もバリアントも削除できない")
        void shown() {
            // 準備：公開開始日時が今で、今公開した商品
            var product = shownAtNow();

            // 操作と確認：フラグが立たず、集約も削除を拒む
            assertNotDeletable(product, product.variants().getFirst().id());
        }

        @Test
        @DisplayName("AC-33 初回表示日時の直前までは、商品を削除できる")
        void deletableJustBeforeShown() {
            // 準備：公開開始日時が今で、今公開した商品
            var product = shownAtNow();

            // 操作と確認：1 秒前の時点では、削除のフラグが立つ
            assertThat(product.actions(NOW.minusSeconds(1)).delete()).isTrue();
        }

        @Test
        @DisplayName("AC-269 C 側に表示されたことのある商品は、削除できない理由に「表示済み」を返す")
        void returnsBlockedReasonWhenShown() {
            // 準備：公開開始日時が今で、今公開した商品
            var product = shownAtNow();

            // 操作と確認：理由が「表示済み」
            assertThat(product.actions(NOW).deleteBlockedReason()).isEqualTo(DeleteBlockedReason.ALREADY_SHOWN);
        }

        @Test
        @DisplayName("AC-269 削除できる商品は、削除できない理由を返さない")
        void returnsNoBlockedReasonWhenDeletable() {
            // 準備：非公開の商品
            var product = productWithVariant();

            // 操作と確認：理由がない
            assertThat(product.actions(NOW).deleteBlockedReason()).isNull();
        }
    }

    @Nested
    @DisplayName("バリアントの追加")
    class AddVariant {

        @Test
        @DisplayName("AC-24 軸がある商品は、バリアントがなければ追加できる")
        void withAxesAndNoVariant() {
            // 準備：軸が色で、バリアントのない商品
            var product = productWithAxes("色");

            // 操作と確認：追加のフラグが立つ
            assertThat(product.actions(NOW).addVariant()).isTrue();
        }

        @Test
        @DisplayName("AC-24 軸がある商品は、バリアントがあっても追加できる")
        void withAxesAndVariant() {
            // 準備：軸が色で、バリアントを 1 つ持つ商品
            var product = productWithAxes("色");
            product.addVariant(UUID.randomUUID(), VariantCode.of("ABC-001-BK"), List.of("黒"), NOW);

            // 操作と確認：追加のフラグが立つ
            assertThat(product.actions(NOW).addVariant()).isTrue();
        }

        @Test
        @DisplayName("AC-24 軸がない商品は、バリアントがなければ追加できる")
        void withoutAxesAndNoVariant() {
            // 準備：軸がなく、バリアントのない商品
            var product = productWithAxes();

            // 操作と確認：追加のフラグが立つ
            assertThat(product.actions(NOW).addVariant()).isTrue();
        }

        @Test
        @DisplayName("AC-24 軸がない商品は、バリアントがあれば追加できない")
        void withoutAxesAndVariant() {
            // 準備：軸がなく、バリアントを 1 つ持つ商品
            var product = productWithAxes();
            product.addVariant(UUID.randomUUID(), VariantCode.of("ABC-001"), List.of(), NOW);

            // 確認：追加のフラグが立たない
            assertThat(product.actions(NOW).addVariant()).isFalse();

            // 操作と確認：集約も、業務のエラー SINGLE_VARIANT_ONLY で追加を拒む
            assertBusinessError(() -> product.addVariant(UUID.randomUUID(), VariantCode.of("ABC-002"), List.of(), NOW),
                    ProductErrorCode.SINGLE_VARIANT_ONLY);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 商品とバリアントを削除できないことを、フラグと集約の両方で確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>商品の削除のフラグが立たない</li>
     *   <li>バリアントの削除のフラグが立たない</li>
     *   <li>集約が、商品の削除を業務のエラー PRODUCT_ALREADY_SHOWN で拒む</li>
     *   <li>集約が、バリアントの削除を業務のエラー PRODUCT_ALREADY_SHOWN で拒む</li>
     * </ul>
     *
     * @param product   確認する商品
     * @param variantId 商品のバリアントの ID（集約が振るので、定数にできない）
     */
    private static void assertNotDeletable(Product product, UUID variantId) {
        assertThat(product.actions(NOW).delete()).isFalse();
        assertThat(product.actions(NOW).deleteVariants()).isFalse();
        assertBusinessError(() -> product.delete(NOW), ProductErrorCode.PRODUCT_ALREADY_SHOWN);
        assertBusinessError(() -> product.deleteVariant(variantId, NOW), ProductErrorCode.PRODUCT_ALREADY_SHOWN);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 軸を指定して、非公開の商品を作る。
     *
     * @param axes 軸の名前
     * @return 非公開の商品（バリアントなし）
     */
    private Product productWithAxes(String... axes) {
        return factory.create(UUID.randomUUID(), ProductCode.of("ABC-001"), "T シャツ", "", TAX_CATEGORY_ID, PriceExclTax.of(1000),
                Set.of(), List.of(axes));
    }

    /**
     * 軸がなく、バリアントを 1 つ持つ非公開の商品を作る。
     *
     * @return 非公開の商品
     */
    private Product productWithVariant() {
        var product = productWithAxes();
        product.addVariant(UUID.randomUUID(), VariantCode.of("ABC-001"), List.of(), NOW.minus(Duration.ofDays(1)));
        return product;
    }

    /**
     * 公開開始日時を今にして、今公開した商品を作る。
     *
     * <p>初回表示日時が今になるので、今の時点で C 側に表示されたことになる。
     *
     * @return 公開中の商品
     */
    private Product shownAtNow() {
        var product = productWithVariant();
        product.changePublicationPeriod(PublicationPeriod.of(NOW, null), NOW);
        product.publish(NOW);
        return product;
    }
}
