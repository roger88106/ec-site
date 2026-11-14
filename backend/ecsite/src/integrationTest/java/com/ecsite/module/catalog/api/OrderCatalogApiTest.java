package com.ecsite.module.catalog.api;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.catalog.api.model.OrderableVariant;
import com.ecsite.module.catalog.api.model.OrderableVariant.AxisValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * カート・注文向けの商品カタログの API の統合テスト
 *
 * <p>商品は B 側の API で作る。テストごとにロールバックする。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る（準備した物から期待値を取り出さない）。
 * 商品品番・ID・画像の URL は実行ごとに作るので、確認の補助に引数で渡す。
 */
@IntegrationTest
@Transactional
class OrderCatalogApiTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 税区分「標準」の税率（%）
     */
    private static final String STANDARD_RATE = "10";

    /**
     * T シャツの商品名
     */
    private static final String PRODUCT_NAME = "T シャツ";

    /**
     * T シャツの税抜価格
     */
    private static final long PRICE_EXCL_TAX = 1800;

    /**
     * 軸「色」
     */
    private static final String COLOR = "色";

    /**
     * 軸「サイズ」
     */
    private static final String SIZE = "サイズ";

    /**
     * 1 つ目のバリアントの色
     */
    private static final String WHITE = "白";

    /**
     * 1 つ目のバリアントのサイズ
     */
    private static final String MEDIUM = "M";

    /**
     * 2 つ目のバリアントの色
     */
    private static final String BLACK = "黒";

    /**
     * 2 つ目のバリアントのサイズ
     */
    private static final String LARGE = "L";

    @Autowired
    OrderCatalogApi api;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    private CatalogTestData catalog;

    /**
     * 税区分「標準」の ID
     */
    private UUID standard;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        catalog = new CatalogTestData(mockMvc, clock);
        standard = catalog.taxCategory("標準", STANDARD_RATE);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("注文に使う情報")
    class VariantInformation {

        @Test
        @DisplayName("AC-92 指定したバリアントの、品番・商品名・軸の名前と値・税抜価格・税区分・表示されるか・販売停止かを返す")
        void returnsVariantInformation() throws Exception {
            // 準備：T シャツ（白・M、黒・L）を公開する
            var product = tShirt().publish();

            // 操作：2 つのバリアントの情報を取得する
            var variants = api.findVariants(product.variantIds);

            // 確認：2 つとも返り、1 つ目は定数どおりの情報を持つ
            assertThat(variants).hasSize(2);
            assertWhiteMediumTShirt(variants.get(product.variantIds.getFirst()), product.variantIds.getFirst(), product.id,
                    product.productCode, product.variantCodes.getFirst());
        }

        @Test
        @DisplayName("AC-92 軸のない商品のバリアントは、軸の値が空")
        void returnsEmptyAxisValuesWithoutAxes() throws Exception {
            // 準備：軸のない商品を公開する
            var product = catalog.product(standard, "軸のない商品", 1000).publish();

            // 操作：バリアントの情報を取得する
            var variants = api.findVariants(product.variantIds);

            // 確認：軸の値が空
            assertThat(variants.get(product.variantIds.getFirst()).axisValues()).isEmpty();
        }

        @Test
        @DisplayName("AC-80 販売停止のバリアントは、販売停止と返す")
        void returnsSalesSuspended() throws Exception {
            // 準備：T シャツを公開し、2 つ目のバリアント（黒・L）だけを販売停止にする
            var product = tShirt().publish().suspend(1);

            // 操作：2 つのバリアントの情報を取得する
            var variants = api.findVariants(product.variantIds);

            // 確認：販売停止にしたバリアントだけが販売停止
            assertThat(variants.get(product.variantIds.get(1)).salesSuspended()).isTrue();
            assertThat(variants.get(product.variantIds.getFirst()).salesSuspended()).isFalse();
        }
    }

    @Nested
    @DisplayName("C 側に表示されない商品")
    class InvisibleProducts {

        @Test
        @DisplayName("AC-80 一度も公開していない商品のバリアントは、表示されないと返す")
        void returnsNeverPublishedAsInvisible() throws Exception {
            // 準備：公開していない商品
            var unpublished = catalog.product(standard, "非公開", 1000);

            // 操作と確認：表示されない
            assertInvisible(unpublished.variantIds.getFirst());
        }

        @Test
        @DisplayName("AC-80 非公開にした商品のバリアントは、表示されないと返す")
        void returnsUnpublishedAsInvisible() throws Exception {
            // 準備：公開した後に非公開にした商品
            var hidden = catalog.product(standard, "非公開にした", 1000).publish().unpublish();

            // 操作と確認：表示されない
            assertInvisible(hidden.variantIds.getFirst());
        }

        @Test
        @DisplayName("AC-80 販売を終了した商品のバリアントは、表示されないと返す")
        void returnsDiscontinuedAsInvisible() throws Exception {
            // 準備：公開した後に販売を終了した商品
            var discontinued = catalog.product(standard, "販売終了", 1000).publish().discontinue();

            // 操作と確認：表示されない
            assertInvisible(discontinued.variantIds.getFirst());
        }

        @Test
        @DisplayName("AC-80 公開期間の外の商品のバリアントは、表示されないと返す")
        void returnsOutOfPeriodAsInvisible() throws Exception {
            // 準備：公開開始日時が明日の商品を公開する
            var reserved = catalog.product(standard, "予約", 1000).period(NOW.plus(Duration.ofDays(1)), null).publish();

            // 操作と確認：まだ表示されない
            assertInvisible(reserved.variantIds.getFirst());
        }
    }

    @Nested
    @DisplayName("取得の対象")
    class Targets {

        @Test
        @DisplayName("存在しないバリアントは結果に含めない")
        void excludesUnknownVariants() throws Exception {
            // 準備：公開した商品のバリアントと、存在しないバリアントの ID
            var product = catalog.product(standard, "商品", 1000).publish();
            var unknown = UUID.randomUUID();

            // 操作：両方を指定して取得する
            var variants = api.findVariants(List.of(product.variantIds.getFirst(), unknown));

            // 確認：存在するバリアントだけが返る
            assertThat(variants).containsOnlyKeys(product.variantIds.getFirst());
        }

        @Test
        @DisplayName("バリアントを指定しなければ、空を返す")
        void returnsEmptyForNoVariants() {
            // 操作と確認：空の指定には空を返す
            assertThat(api.findVariants(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("画像")
    class Images {

        @Test
        @DisplayName("AC-38 画像を指定したバリアントは、その画像の URL を返す")
        void returnsVariantImage() throws Exception {
            // 準備：画像 2 枚の商品の 2 つ目のバリアントに、2 枚目の画像を指定する
            var product = productWithSecondVariantImage();

            // 操作：バリアントの情報を取得する
            var variants = api.findVariants(product.variantIds);

            // 確認：2 枚目の画像の URL
            assertThat(variants.get(product.variantIds.get(1)).imageUrl()).isEqualTo(product.imageUrls.get(1));
        }

        @Test
        @DisplayName("AC-38 画像を指定していないバリアントは、商品の代表画像の URL を返す")
        void returnsRepresentativeImage() throws Exception {
            // 準備：画像 2 枚の商品の 2 つ目のバリアントにだけ、2 枚目の画像を指定する
            var product = productWithSecondVariantImage();

            // 操作：バリアントの情報を取得する
            var variants = api.findVariants(product.variantIds);

            // 確認：指定のない 1 つ目のバリアントは、先頭の画像（代表画像）の URL
            assertThat(variants.get(product.variantIds.getFirst()).imageUrl()).isEqualTo(product.imageUrls.getFirst());
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * T シャツ（白・M）のバリアントの、注文に使う情報を確認する。
     *
     * <p>確認すること（期待値は定数、引数と、準備で作った税区分）：
     * <ul>
     *   <li>バリアント ID</li>
     *   <li>商品 ID</li>
     *   <li>商品品番</li>
     *   <li>バリアント品番</li>
     *   <li>商品名</li>
     *   <li>軸の名前と値（並びを含む）</li>
     *   <li>税抜価格</li>
     *   <li>税区分 ID</li>
     *   <li>画像の URL がない（画像のない商品）</li>
     *   <li>表示される</li>
     *   <li>販売停止でない</li>
     * </ul>
     *
     * @param variant     確認する情報
     * @param variantId   バリアント ID（実行ごとに作るので、定数にできない）
     * @param productId   商品 ID（実行ごとに作るので、定数にできない）
     * @param productCode 商品品番（補助が実行ごとに振るので、定数にできない）
     * @param variantCode バリアント品番（補助が商品品番から振るので、定数にできない）
     */
    private void assertWhiteMediumTShirt(OrderableVariant variant, UUID variantId, UUID productId, String productCode,
                                         String variantCode) {
        assertThat(variant.variantId()).isEqualTo(variantId);
        assertThat(variant.productId()).isEqualTo(productId);
        assertThat(variant.productCode()).isEqualTo(productCode);
        assertThat(variant.variantCode()).isEqualTo(variantCode);
        assertThat(variant.productName()).isEqualTo(PRODUCT_NAME);
        assertThat(variant.axisValues()).containsExactly(new AxisValue(COLOR, WHITE), new AxisValue(SIZE, MEDIUM));
        assertThat(variant.priceExclTax()).isEqualTo(Money.of(PRICE_EXCL_TAX));
        assertThat(variant.taxCategoryId()).isEqualTo(standard);
        assertThat(variant.imageUrl()).isNull();
        assertThat(variant.visible()).isTrue();
        assertThat(variant.salesSuspended()).isFalse();
    }

    /**
     * バリアントが、表示されないと返ることを確認する。
     *
     * @param variantId バリアント ID
     */
    private void assertInvisible(UUID variantId) {
        assertThat(api.findVariants(List.of(variantId)).get(variantId).visible()).isFalse();
    }

    // ============================== 準備の補助 ==============================

    /**
     * T シャツ（白・M、黒・L）を、定数から登録する（公開しない）。
     *
     * @return 商品
     */
    private CatalogTestData.TestProduct tShirt() throws Exception {
        return catalog.product(standard, PRODUCT_NAME, PRICE_EXCL_TAX, COLOR, SIZE).variant(WHITE, MEDIUM).variant(BLACK, LARGE);
    }

    /**
     * 色の軸と画像 2 枚を持つ商品を登録し、2 つ目のバリアントにだけ 2 枚目の画像を指定して公開する。
     *
     * @return 商品
     */
    private CatalogTestData.TestProduct productWithSecondVariantImage() throws Exception {
        return catalog.product(standard, "画像あり", 1000, COLOR)
                .variant(WHITE).variant(BLACK).image().image().variantImage(1, 1).publish();
    }
}
