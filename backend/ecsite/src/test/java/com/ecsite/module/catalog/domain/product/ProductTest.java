package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import com.ecsite.module.catalog.event.ProductPriceChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 商品（集約）の登録・編集・復元の単体テスト
 *
 * <p>バリアントは {@code ProductVariantTest}、公開の状態・公開期間・初回表示日時・商品の削除は
 * {@code ProductPublicationTest}、商品画像は {@code ProductImageTest} が確認する。現在時刻は引数で固定する。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る（準備した物から期待値を取り出さない）。
 */
class ProductTest {

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
     * 所属カテゴリの ID
     */
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000001001");

    /**
     * 2 つ目の所属カテゴリの ID
     */
    private static final UUID OTHER_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000001002");

    /**
     * 商品品番
     */
    private static final String PRODUCT_CODE = "ABC-001";

    /**
     * 商品名
     */
    private static final String NAME = "T シャツ";

    /**
     * 説明
     */
    private static final String DESCRIPTION = "綿 100%";

    /**
     * 税抜価格（円）
     */
    private static final long PRICE_YEN = 1000;

    /**
     * バリアント軸の名前（並び順）
     */
    private static final List<String> AXES = List.of("色", "サイズ");

    /**
     * 編集後の商品名
     */
    private static final String NEW_NAME = "ポロシャツ";

    /**
     * 編集後の説明
     */
    private static final String NEW_DESCRIPTION = "綿 50%";

    /**
     * 編集後の税区分 ID
     */
    private static final UUID NEW_TAX_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /**
     * 編集後の所属カテゴリの ID
     */
    private static final UUID NEW_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000001003");

    /**
     * 編集後の税抜価格（円）
     */
    private static final long NEW_PRICE_YEN = 2000;

    /**
     * 商品の生成
     */
    private final ProductFactory factory = new ProductFactory();

    // ============================== テスト ==============================

    @Nested
    @DisplayName("登録")
    class Create {

        @Test
        @DisplayName("AC-13 指定した内容で登録される")
        void createsWithGivenContent() {
            // 操作：品番・商品名・説明・税区分・税抜価格・所属カテゴリ・軸を指定して登録する
            var product = registered();

            // 確認：指定した内容を持つ
            assertRegisteredAsSpecified(product);
        }

        @Test
        @DisplayName("AC-13 所属カテゴリが 0 個でも登録できる")
        void acceptsNoCategory() {
            // 操作：所属カテゴリを指定せずに登録する
            var product = factory.create(ProductCode.of(PRODUCT_CODE), NAME, DESCRIPTION, TAX_CATEGORY_ID,
                    PriceExclTax.of(PRICE_YEN), Set.of(), AXES);

            // 確認：所属カテゴリがない
            assertThat(product.categoryIds()).isEmpty();
        }

        @Test
        @DisplayName("AC-13 所属カテゴリを複数指定して登録できる")
        void acceptsMultipleCategories() {
            // 操作：所属カテゴリを 2 つ指定して登録する
            var product = factory.create(ProductCode.of(PRODUCT_CODE), NAME, DESCRIPTION, TAX_CATEGORY_ID,
                    PriceExclTax.of(PRICE_YEN), Set.of(CATEGORY_ID, OTHER_CATEGORY_ID), AXES);

            // 確認：指定した 2 つのカテゴリに所属する
            assertThat(product.categoryIds()).containsExactlyInAnyOrder(CATEGORY_ID, OTHER_CATEGORY_ID);
        }

        @Test
        @DisplayName("AC-17 登録直後の商品は非公開")
        void createsUnpublished() {
            // 操作：登録する
            var product = registered();

            // 確認：非公開
            assertThat(product.status()).isEqualTo(PublicationStatus.UNPUBLISHED);
        }

        @Test
        @DisplayName("登録直後の商品は、まだ C 側に表示されていない")
        void hasNoFirstShownAtWhenCreated() {
            // 操作：登録する
            var product = registered();

            // 確認：初回表示日時が空
            assertThat(product.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("登録直後の商品は、バリアントを持たない")
        void hasNoVariantsWhenCreated() {
            // 操作：登録する
            var product = registered();

            // 確認：バリアントがない
            assertThat(product.variants()).isEmpty();
        }

        @Test
        @DisplayName("登録直後の商品のバージョンは 0")
        void startsAtVersionZero() {
            // 操作：登録する
            var product = registered();

            // 確認：バージョンが 0
            assertThat(product.version()).isZero();
        }
    }

    @Nested
    @DisplayName("バリアント軸")
    class Axes {

        @Test
        @DisplayName("AC-13 軸は 0 個でもよい")
        void acceptsNoAxis() {
            // 操作と確認：軸なしで登録できる
            assertThatCode(() -> productWithAxes()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-13 軸は 3 つまで指定できる")
        void acceptsThreeAxes() {
            // 操作と確認：軸 3 つで登録できる
            assertThatCode(() -> productWithAxes("色", "サイズ", "素材")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-13 軸が 4 つ以上なら拒否される")
        void rejectsFourAxes() {
            // 操作と確認：軸 4 つの登録は、業務のエラー INVALID_VARIANT_AXES で拒否される
            assertBusinessError(() -> productWithAxes("色", "サイズ", "素材", "柄"), ProductErrorCode.INVALID_VARIANT_AXES);
        }

        @Test
        @DisplayName("軸の名前は 30 文字まで指定できる")
        void acceptsAxisNameUpTo30Characters() {
            // 操作と確認：30 文字の軸の名前で登録できる
            assertThatCode(() -> productWithAxes("あ".repeat(30))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("軸の名前が 31 文字以上なら拒否される")
        void rejectsAxisNameOver30Characters() {
            // 操作と確認：業務のエラー INVALID_VARIANT_AXES で拒否される
            assertBusinessError(() -> productWithAxes("あ".repeat(31)), ProductErrorCode.INVALID_VARIANT_AXES);
        }

        @Test
        @DisplayName("軸の名前が空白だけなら拒否される")
        void rejectsBlankAxisName() {
            // 操作と確認：業務のエラー INVALID_VARIANT_AXES で拒否される
            assertBusinessError(() -> productWithAxes(" "), ProductErrorCode.INVALID_VARIANT_AXES);
        }

        @Test
        @DisplayName("軸の名前が重複すれば拒否される")
        void rejectsDuplicatedAxisName() {
            // 操作と確認：業務のエラー INVALID_VARIANT_AXES で拒否される
            assertBusinessError(() -> productWithAxes("色", "色"), ProductErrorCode.INVALID_VARIANT_AXES);
        }
    }

    @Nested
    @DisplayName("商品名と説明")
    class NameAndDescription {

        @Test
        @DisplayName("商品名は 200 文字まで指定できる")
        void acceptsNameUpTo200Characters() {
            // 操作と確認：200 文字の商品名で登録できる
            assertThatCode(() -> productNamed("あ".repeat(200))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("商品名が 201 文字以上なら拒否される")
        void rejectsNameOver200Characters() {
            // 操作と確認：業務のエラー INVALID_PRODUCT_NAME で拒否される
            assertBusinessError(() -> productNamed("あ".repeat(201)), ProductErrorCode.INVALID_PRODUCT_NAME);
        }

        @Test
        @DisplayName("商品名が空白だけなら拒否される")
        void rejectsBlankName() {
            // 操作と確認：業務のエラー INVALID_PRODUCT_NAME で拒否される
            assertBusinessError(() -> productNamed(" "), ProductErrorCode.INVALID_PRODUCT_NAME);
        }

        @Test
        @DisplayName("説明は空でもよい")
        void acceptsEmptyDescription() {
            // 操作と確認：空の説明で登録できる
            assertThatCode(() -> productDescribed("")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("説明は 10,000 文字まで指定できる")
        void acceptsDescriptionUpTo10000Characters() {
            // 操作と確認：10,000 文字の説明で登録できる
            assertThatCode(() -> productDescribed("あ".repeat(10_000))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("説明が 10,001 文字以上なら拒否される")
        void rejectsDescriptionOver10000Characters() {
            // 操作と確認：業務のエラー INVALID_PRODUCT_DESCRIPTION で拒否される
            assertBusinessError(() -> productDescribed("あ".repeat(10_001)), ProductErrorCode.INVALID_PRODUCT_DESCRIPTION);
        }
    }

    @Nested
    @DisplayName("編集")
    class Update {

        @Test
        @DisplayName("AC-18 商品名、説明、税区分、税抜価格、所属カテゴリを変更できる")
        void updates() {
            // 準備：登録した商品
            var product = registered();

            // 操作：全ての項目を変える
            updateAll(product);

            // 確認：変えた値になる
            assertUpdated(product);
        }

        @Test
        @DisplayName("税抜価格が変わると、価格変更のイベントが登録される")
        void registersPriceChangedEvent() {
            // 準備：登録した商品
            var product = registered();

            // 操作：税抜価格を変える
            updateAll(product);

            // 確認：価格変更のイベントだけが登録されている
            assertPriceChangedEventOnly(product);
        }

        @Test
        @DisplayName("税抜価格が変わらなければ、価格変更のイベントは登録されない")
        void doesNotRegisterEventWithoutPriceChange() {
            // 準備：登録した商品
            var product = registered();

            // 操作：税抜価格を変えずに、ほかの項目を変える
            product.update(NEW_NAME, "", TAX_CATEGORY_ID, PriceExclTax.of(PRICE_YEN), Set.of(), NOW);

            // 確認：イベントがない
            assertThat(product.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("変更後の商品名が空白だけなら拒否される")
        void rejectsInvalidName() {
            // 準備：登録した商品
            var product = registered();

            // 操作と確認：業務のエラー INVALID_PRODUCT_NAME で拒否される
            assertBusinessError(() -> product.update(" ", "", TAX_CATEGORY_ID, PriceExclTax.of(NEW_PRICE_YEN), Set.of(), NOW),
                    ProductErrorCode.INVALID_PRODUCT_NAME);
        }

        @Test
        @DisplayName("拒否された編集は、ほかの項目も変えない")
        void keepsValuesWhenRejected() {
            // 準備：登録した商品
            var product = registered();

            // 確認（前提）：空白の商品名で、ほかの全ての項目も変える編集が拒否される
            assertBusinessError(() -> product.update(" ", NEW_DESCRIPTION, NEW_TAX_CATEGORY_ID, PriceExclTax.of(NEW_PRICE_YEN),
                            Set.of(NEW_CATEGORY_ID), NOW),
                    ProductErrorCode.INVALID_PRODUCT_NAME);

            // 確認：全ての項目が登録したときのまま
            assertRegisteredAsSpecified(product);
        }

        @Test
        @DisplayName("AC-18 販売終了の商品も編集できる")
        void updatesDiscontinuedProduct() {
            // 準備：販売終了にした商品
            var product = registered();
            product.discontinue(NOW);

            // 操作：商品名を変える
            product.update(NEW_NAME, DESCRIPTION, TAX_CATEGORY_ID, PriceExclTax.of(PRICE_YEN), Set.of(CATEGORY_ID), NOW);

            // 確認：変えた商品名になる
            assertThat(product.name()).isEqualTo(NEW_NAME);
        }
    }

    @Nested
    @DisplayName("復元")
    class Restore {

        @Test
        @DisplayName("スナップショットから、同じ内容の商品に復元できる")
        void restores() {
            // 準備：バリアントを持つ公開中の商品
            var product = publishedWithVariant();

            // 操作：スナップショットから復元する
            var restored = factory.restore(product.toSnapshot());

            // 確認：スナップショットが元の商品と同じ
            assertThat(restored.toSnapshot()).isEqualTo(product.toSnapshot());
        }

        @Test
        @DisplayName("復元した商品は、イベントを持たない")
        void restoresWithoutEvents() {
            // 準備：バリアントを持つ公開中の商品
            var product = publishedWithVariant();

            // 操作：スナップショットから復元する
            var restored = factory.restore(product.toSnapshot());

            // 確認：イベントがない
            assertThat(restored.pullEvents()).isEmpty();
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 登録した商品が、指定した内容（{@link #registered()}）を持つことを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>ID が振られている</li>
     *   <li>商品品番</li>
     *   <li>商品名</li>
     *   <li>説明</li>
     *   <li>税区分 ID</li>
     *   <li>税抜価格</li>
     *   <li>所属カテゴリ</li>
     *   <li>軸（並びを含む）</li>
     * </ul>
     *
     * @param product 確認する商品
     */
    private static void assertRegisteredAsSpecified(Product product) {
        assertThat(product.id()).isNotNull();
        assertThat(product.productCode()).isEqualTo(ProductCode.of(PRODUCT_CODE));
        assertThat(product.name()).isEqualTo(NAME);
        assertThat(product.description()).isEqualTo(DESCRIPTION);
        assertThat(product.taxCategoryId()).isEqualTo(TAX_CATEGORY_ID);
        assertThat(product.priceExclTax()).isEqualTo(PriceExclTax.of(PRICE_YEN));
        assertThat(product.categoryIds()).containsExactly(CATEGORY_ID);
        assertThat(product.axes()).containsExactlyElementsOf(AXES);
    }

    /**
     * 商品が、編集後の値（{@link #updateAll(Product)}）を持つことを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>商品名</li>
     *   <li>説明</li>
     *   <li>税区分 ID</li>
     *   <li>税抜価格</li>
     *   <li>所属カテゴリ</li>
     * </ul>
     *
     * @param product 確認する商品
     */
    private static void assertUpdated(Product product) {
        assertThat(product.name()).isEqualTo(NEW_NAME);
        assertThat(product.description()).isEqualTo(NEW_DESCRIPTION);
        assertThat(product.taxCategoryId()).isEqualTo(NEW_TAX_CATEGORY_ID);
        assertThat(product.priceExclTax()).isEqualTo(PriceExclTax.of(NEW_PRICE_YEN));
        assertThat(product.categoryIds()).containsExactly(NEW_CATEGORY_ID);
    }

    /**
     * 価格変更のイベントだけが登録されていることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>登録されたイベントが 1 件だけ</li>
     *   <li>種類が {@link ProductPriceChanged}</li>
     *   <li>商品 ID がこの商品</li>
     *   <li>税抜価格が編集後の価格</li>
     *   <li>起きた日時が編集した時刻</li>
     * </ul>
     *
     * @param product 確認する商品
     */
    private static void assertPriceChangedEventOnly(Product product) {
        assertThat(product.pullEvents()).singleElement().isInstanceOfSatisfying(ProductPriceChanged.class, event -> {
            assertThat(event.productId()).isEqualTo(product.id());
            assertThat(event.priceExclTax()).isEqualTo(Money.of(NEW_PRICE_YEN));
            assertThat(event.occurredAt()).isEqualTo(NOW);
        });
    }

    // ============================== 準備の補助 ==============================

    /**
     * 全ての項目を定数で指定して、商品を登録する。
     *
     * @return 非公開の商品（バリアントなし）
     */
    private Product registered() {
        return factory.create(ProductCode.of(PRODUCT_CODE), NAME, DESCRIPTION, TAX_CATEGORY_ID, PriceExclTax.of(PRICE_YEN),
                Set.of(CATEGORY_ID), AXES);
    }

    /**
     * 商品の全ての項目を、編集後の値に変える。
     *
     * @param product 編集する商品
     */
    private static void updateAll(Product product) {
        product.update(NEW_NAME, NEW_DESCRIPTION, NEW_TAX_CATEGORY_ID, PriceExclTax.of(NEW_PRICE_YEN), Set.of(NEW_CATEGORY_ID), NOW);
    }

    /**
     * 軸を指定して、非公開の商品を作る。
     *
     * @param axes 軸の名前
     * @return 非公開の商品（バリアントなし）
     */
    private Product productWithAxes(String... axes) {
        return factory.create(ProductCode.of(PRODUCT_CODE), NAME, "", TAX_CATEGORY_ID, PriceExclTax.of(PRICE_YEN),
                Set.of(), List.of(axes));
    }

    /**
     * 商品名を指定して、軸のない商品を作る。
     *
     * @param name 商品名
     * @return 非公開の商品（バリアントなし）
     */
    private Product productNamed(String name) {
        return factory.create(ProductCode.of(PRODUCT_CODE), name, "", TAX_CATEGORY_ID, PriceExclTax.of(PRICE_YEN), Set.of(), List.of());
    }

    /**
     * 説明を指定して、軸のない商品を作る。
     *
     * @param description 説明
     * @return 非公開の商品（バリアントなし）
     */
    private Product productDescribed(String description) {
        return factory.create(ProductCode.of(PRODUCT_CODE), NAME, description, TAX_CATEGORY_ID, PriceExclTax.of(PRICE_YEN),
                Set.of(), List.of());
    }

    /**
     * 軸が 2 つで、バリアントを 1 つ持つ公開中の商品を作る。
     *
     * @return 公開中の商品
     */
    private Product publishedWithVariant() {
        var product = registered();
        product.addVariant(VariantCode.of("ABC-001-WH-M"), List.of("白", "M"), NOW);
        product.publish(NOW);
        return product;
    }
}
