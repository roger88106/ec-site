package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.model.id.AssetId;
import com.ecsite.common.model.id.ProductId;
import com.ecsite.common.model.id.TaxCategoryId;
import com.ecsite.common.model.id.VariantId;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.ProductImageId;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import com.ecsite.module.catalog.domain.product.vo.PublicationStatus;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 商品・バリアント・商品画像の不変条件の単体テスト
 *
 * <p>コンストラクターが、条件に違反する値を IllegalArgumentException で拒むことを確認する。
 * 例外のメッセージには値を入れない。
 */
class ProductInvariantsTest {

    // ============================== 定数 ==============================

    /**
     * 1 商品あたりの画像の上限（枚）
     */
    private static final int MAX_IMAGES = 20;

    /**
     * 初めて表示した日時（過去）
     */
    private static final Instant SHOWN_AT = Instant.parse("2026-10-01T00:00:00Z");

    /**
     * 公開終了日時
     */
    private static final Instant PUBLISH_END = Instant.parse("2026-10-31T00:00:00Z");

    /**
     * メッセージに入っていないことを確認する、負の表示順
     */
    private static final int NEGATIVE_ORDER = -123456;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("バリアント")
    class VariantInvariants {

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 100})
        @DisplayName("表示順が 0 以上なら作れる")
        void acceptsNonNegativeDisplayOrder(int displayOrder) {
            assertThatCode(() -> variant("A-1", List.of(), displayOrder)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("表示順が負なら、値を含まないメッセージで拒む")
        void rejectsNegativeDisplayOrder() {
            assertThatThrownBy(() -> variant("A-1", List.of(), NEGATIVE_ORDER))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(String.valueOf(NEGATIVE_ORDER));
        }

        @Test
        @DisplayName("表示順が -1 なら拒む")
        void rejectsMinusOne() {
            assertThatThrownBy(() -> variant("A-1", List.of(), -1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("商品の画像")
    class ImageInvariants {

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 100})
        @DisplayName("表示順が 0 以上なら作れる")
        void acceptsNonNegativeDisplayOrder(int displayOrder) {
            assertThatCode(() -> new ProductImage(new ProductImageId(UUID.randomUUID()), new AssetId(UUID.randomUUID()), displayOrder))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("表示順が負なら、値を含まないメッセージで拒む")
        void rejectsNegativeDisplayOrder() {
            assertThatThrownBy(() -> new ProductImage(new ProductImageId(UUID.randomUUID()), new AssetId(UUID.randomUUID()), NEGATIVE_ORDER))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(String.valueOf(NEGATIVE_ORDER));
        }

        @Test
        @DisplayName("表示順が -1 なら拒む")
        void rejectsMinusOne() {
            assertThatThrownBy(() -> new ProductImage(new ProductImageId(UUID.randomUUID()), new AssetId(UUID.randomUUID()), -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("商品の画像の数")
    class ImageCount {

        @Test
        @DisplayName("上限と同じ数なら作れる")
        void acceptsMaxImages() {
            assertThatCode(() -> new Product(snapshot(images(MAX_IMAGES), List.of(), List.of(),
                    PublicationStatus.UNPUBLISHED, null, PublicationPeriod.UNLIMITED))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("上限を超えるなら拒む")
        void rejectsOverMaxImages() {
            assertThatThrownBy(() -> new Product(snapshot(images(MAX_IMAGES + 1), List.of(), List.of(),
                    PublicationStatus.UNPUBLISHED, null, PublicationPeriod.UNLIMITED)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(String.valueOf(MAX_IMAGES + 1));
        }
    }

    @Nested
    @DisplayName("軸がない商品のバリアントの数")
    class VariantCountWithoutAxes {

        @Test
        @DisplayName("バリアントが 1 つなら作れる")
        void acceptsOneVariant() {
            assertThatCode(() -> new Product(snapshot(List.of(), List.of(), List.of(variant("A-1", List.of(), 1)),
                    PublicationStatus.UNPUBLISHED, null, PublicationPeriod.UNLIMITED))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("バリアントが 2 つなら拒む")
        void rejectsTwoVariants() {
            assertThatThrownBy(() -> new Product(snapshot(List.of(), List.of(),
                    List.of(variant("A-1", List.of(), 1), variant("A-2", List.of(), 2)),
                    PublicationStatus.UNPUBLISHED, null, PublicationPeriod.UNLIMITED)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("軸があれば、バリアントが 2 つでも作れる")
        void acceptsTwoVariantsWithAxis() {
            assertThatCode(() -> new Product(snapshot(List.of(), List.of("色"),
                    List.of(variant("A-1", List.of("白"), 1), variant("A-2", List.of("黒"), 2)),
                    PublicationStatus.UNPUBLISHED, null, PublicationPeriod.UNLIMITED))).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("バリアントの軸の値の数")
    class AxisValueCount {

        @Test
        @DisplayName("商品の軸の数と同じなら作れる")
        void acceptsSameCount() {
            assertThatCode(() -> new Product(snapshot(List.of(), List.of("色", "サイズ"),
                    List.of(variant("A-1", List.of("白", "M"), 1)),
                    PublicationStatus.UNPUBLISHED, null, PublicationPeriod.UNLIMITED))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("商品の軸の数より少ないなら拒む")
        void rejectsFewerValues() {
            assertThatThrownBy(() -> new Product(snapshot(List.of(), List.of("色", "サイズ"),
                    List.of(variant("A-1", List.of("白"), 1)),
                    PublicationStatus.UNPUBLISHED, null, PublicationPeriod.UNLIMITED)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining("白");
        }

        @Test
        @DisplayName("商品の軸の数より多いなら拒む")
        void rejectsMoreValues() {
            assertThatThrownBy(() -> new Product(snapshot(List.of(), List.of("色"),
                    List.of(variant("A-1", List.of("白", "M"), 1)),
                    PublicationStatus.UNPUBLISHED, null, PublicationPeriod.UNLIMITED)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("軸がない商品のバリアントが軸の値を持つなら拒む")
        void rejectsValuesWithoutAxes() {
            assertThatThrownBy(() -> new Product(snapshot(List.of(), List.of(),
                    List.of(variant("A-1", List.of("白"), 1)),
                    PublicationStatus.UNPUBLISHED, null, PublicationPeriod.UNLIMITED)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("公開状態と初めて表示した日時の整合")
    class StatusAndFirstShownAt {

        @ParameterizedTest
        @EnumSource(value = PublicationStatus.class, names = {"UNPUBLISHED", "DISCONTINUED"})
        @DisplayName("非公開と販売終了は、日時がなくても過去の日時があっても作れる")
        void acceptsNullAndShownForNotPublished(PublicationStatus status) {
            assertThatCode(() -> new Product(snapshot(List.of(), List.of(), List.of(), status, null,
                    PublicationPeriod.UNLIMITED))).doesNotThrowAnyException();
            assertThatCode(() -> new Product(snapshot(List.of(), List.of(), List.of(), status, SHOWN_AT,
                    PublicationPeriod.UNLIMITED))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("公開で日時があれば作れる")
        void acceptsPublishedWithFirstShownAt() {
            assertThatCode(() -> new Product(snapshot(List.of(), List.of(), List.of(), PublicationStatus.PUBLISHED,
                    SHOWN_AT, PublicationPeriod.UNLIMITED))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("公開で日時がなく、公開終了があれば作れる（終了済みで表示されなかった）")
        void acceptsPublishedWithoutFirstShownAtWhenPeriodEnds() {
            assertThatCode(() -> new Product(snapshot(List.of(), List.of(), List.of(), PublicationStatus.PUBLISHED,
                    null, PublicationPeriod.of(null, PUBLISH_END)))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("公開で日時がなく、公開終了もなければ拒む（表示される商品は日時を持つ）")
        void rejectsPublishedWithoutFirstShownAtAndEnd() {
            assertThatThrownBy(() -> new Product(snapshot(List.of(), List.of(), List.of(), PublicationStatus.PUBLISHED,
                    null, PublicationPeriod.UNLIMITED)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * バリアントを作る。
     *
     * @param code         バリアント品番
     * @param axisValues   軸の値
     * @param displayOrder 表示順
     * @return バリアント
     */
    private static Variant variant(String code, List<String> axisValues, int displayOrder) {
        return new Variant(new VariantId(UUID.randomUUID()), VariantCode.of(code), axisValues, displayOrder, false, null);
    }

    /**
     * 表示順を 1 から振った画像を作る。
     *
     * @param count 枚数
     * @return 画像
     */
    private static List<ProductImage> images(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(order -> new ProductImage(new ProductImageId(UUID.randomUUID()), new AssetId(UUID.randomUUID()), order))
                .toList();
    }

    /**
     * 商品のスナップショットを作る。
     *
     * @param images            画像
     * @param axes              軸の名前
     * @param variants          バリアント
     * @param status            公開状態
     * @param firstShownAt      初めて表示した日時
     * @param publicationPeriod 公開期間
     * @return スナップショット
     */
    private static Product.Snapshot snapshot(List<ProductImage> images, List<String> axes, List<Variant> variants,
                                             PublicationStatus status, Instant firstShownAt,
                                             PublicationPeriod publicationPeriod) {
        return new Product.Snapshot(new ProductId(UUID.randomUUID()), ProductCode.of("ABC-001"), "T シャツ", "",
                new TaxCategoryId(UUID.fromString("00000000-0000-0000-0000-000000000001")), PriceExclTax.of(1000), Set.of(), axes, status,
                firstShownAt, publicationPeriod, images, variants, 0);
    }
}
