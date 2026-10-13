package com.ecsite.module.catalog.domain.product;

import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
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

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 商品にできる操作（画面に返す旗）の単体テスト。<br />
 * 旗の値が、集約の規則（操作を受け付けるか、拒むか）と同じになることを確かめる。
 */
class ProductActionsTest {

    /**
     * 現在：2026-10-08 00:00（日本時間）
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 税区分 ID（税モジュールの ID なので、存在しなくてよい）
     */
    private static final UUID TAX_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ProductFactory factory = new ProductFactory();

    @Nested
    @DisplayName("公開の状態の操作")
    class Status {

        @Test
        @DisplayName("非公開なら公開と販売終了、公開なら非公開と販売終了ができ、販売終了なら何もできない")
        void byStatus() {
            var product = productWithVariant();
            assertThat(product.actions(NOW))
                    .extracting(ProductActions::publish, ProductActions::unpublish, ProductActions::discontinue)
                    .containsExactly(true, false, true);

            product.publish(NOW);
            assertThat(product.actions(NOW))
                    .extracting(ProductActions::publish, ProductActions::unpublish, ProductActions::discontinue)
                    .containsExactly(false, true, true);

            product.discontinue(NOW);
            assertThat(product.actions(NOW))
                    .extracting(ProductActions::publish, ProductActions::unpublish, ProductActions::discontinue)
                    .containsExactly(false, false, false);
        }
    }

    @Nested
    @DisplayName("AC-71 削除")
    class Delete {

        @Test
        @DisplayName("AC-71 C 側に表示されたことがなければ、商品とバリアントを削除できる（初回表示日時が空、または未来）")
        void neverShown() {
            var unpublished = productWithVariant();
            var scheduled = productWithVariant();
            scheduled.changePublicationPeriod(PublicationPeriod.of(NOW.plusSeconds(1), null), NOW);
            scheduled.publish(NOW);

            for (var product : List.of(unpublished, scheduled)) {
                assertThat(product.actions(NOW).delete()).isTrue();
                assertThat(product.actions(NOW).deleteVariants()).isTrue();
                assertThatCode(() -> product.delete(NOW)).doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("AC-71 初回表示日時ちょうどから後は、商品もバリアントも削除できない")
        void shown() {
            var product = productWithVariant();
            product.changePublicationPeriod(PublicationPeriod.of(NOW, null), NOW);
            product.publish(NOW);
            var variantId = product.variants().getFirst().id();

            assertThat(product.actions(NOW).delete()).isFalse();
            assertThat(product.actions(NOW).deleteVariants()).isFalse();
            assertThat(product.actions(NOW.minusSeconds(1)).delete()).isTrue();
            assertBusinessError(() -> product.delete(NOW), ProductErrorCode.PRODUCT_ALREADY_SHOWN);
            assertBusinessError(() -> product.deleteVariant(variantId, NOW), ProductErrorCode.PRODUCT_ALREADY_SHOWN);
        }
    }

    @Nested
    @DisplayName("AC-24 バリアントの追加")
    class AddVariant {

        @Test
        @DisplayName("AC-24 軸がある商品は、バリアントがあっても追加できる")
        void withAxes() {
            var product = productWithAxes("色");
            assertThat(product.actions(NOW).addVariant()).isTrue();

            product.addVariant(VariantCode.of("ABC-001-BK"), List.of("黒"), NOW);

            assertThat(product.actions(NOW).addVariant()).isTrue();
        }

        @Test
        @DisplayName("AC-24 軸がない商品は、バリアントがないときだけ追加できる")
        void withoutAxes() {
            var product = productWithAxes();
            assertThat(product.actions(NOW).addVariant()).isTrue();

            product.addVariant(VariantCode.of("ABC-001"), List.of(), NOW);

            assertThat(product.actions(NOW).addVariant()).isFalse();
            assertBusinessError(() -> product.addVariant(VariantCode.of("ABC-002"), List.of(), NOW),
                    ProductErrorCode.SINGLE_VARIANT_ONLY);
        }
    }

    // ============================== 補助 ==============================

    /**
     * 軸を指定して、非公開の商品を作る（バリアントなし）
     */
    private Product productWithAxes(String... axes) {
        return factory.create(ProductCode.of("ABC-001"), "T シャツ", "", TAX_CATEGORY_ID, PriceExclTax.of(1000),
                Set.of(), List.of(axes));
    }

    /**
     * 軸がなく、バリアントを 1 つ持つ非公開の商品
     */
    private Product productWithVariant() {
        var product = productWithAxes();
        product.addVariant(VariantCode.of("ABC-001"), List.of(), NOW.minus(Duration.ofDays(1)));
        return product;
    }
}
