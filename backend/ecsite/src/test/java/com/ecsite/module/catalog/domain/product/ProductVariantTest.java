package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import com.ecsite.module.catalog.event.VariantDeleted;
import com.ecsite.module.catalog.event.VariantRegistered;
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
 * 商品（集約）のバリアントの単体テスト
 *
 * <p>バリアントの登録・並び順・販売停止・削除を確認する。現在時刻は引数で固定する。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る（準備した物から期待値を取り出さない）。
 */
class ProductVariantTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 1 日
     */
    private static final Duration ONE_DAY = Duration.ofDays(1);

    /**
     * 税区分 ID
     *
     * <p>税モジュールの ID なので、存在しなくてよい。
     */
    private static final UUID TAX_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * 白・M のバリアント品番
     */
    private static final String WHITE_M_CODE = "ABC-001-WH-M";

    /**
     * 白・M の軸の値（色、サイズの順）
     */
    private static final List<String> WHITE_M = List.of("白", "M");

    /**
     * 黒・M のバリアント品番
     */
    private static final String BLACK_M_CODE = "ABC-001-BK-M";

    /**
     * 黒・M の軸の値（色、サイズの順）
     */
    private static final List<String> BLACK_M = List.of("黒", "M");

    /**
     * 商品の生成
     */
    private final ProductFactory factory = new ProductFactory();

    // ============================== テスト ==============================

    @Nested
    @DisplayName("バリアントの登録")
    class AddVariant {

        @Test
        @DisplayName("AC-20 バリアント品番と軸の値を指定して登録できる")
        void addsVariant() {
            // 準備：軸が色・サイズの商品
            var product = productWithAxes("色", "サイズ");

            // 操作：白・M のバリアントを登録する
            product.addVariant(VariantCode.of(WHITE_M_CODE), WHITE_M, NOW);

            // 確認：指定した品番と軸の値を持つ
            assertThat(product.variants()).singleElement().satisfies(variant -> {
                assertThat(variant.variantCode()).isEqualTo(VariantCode.of(WHITE_M_CODE));
                assertThat(variant.axisValues()).containsExactlyElementsOf(WHITE_M);
            });
        }

        @Test
        @DisplayName("AC-20 登録したバリアントは、並びの最後に加わる")
        void appendsToEnd() {
            // 準備：軸が色・サイズの商品
            var product = productWithAxes("色", "サイズ");

            // 操作：白・M、黒・M の順に登録する
            var first = product.addVariant(VariantCode.of(WHITE_M_CODE), WHITE_M, NOW);
            var second = product.addVariant(VariantCode.of(BLACK_M_CODE), BLACK_M, NOW);

            // 確認：登録した順に並ぶ
            assertThat(product.variants()).extracting(Variant::id).containsExactly(first, second);
        }

        @Test
        @DisplayName("AC-20 登録したバリアントは販売停止でない")
        void addsOnSale() {
            // 準備：軸が色・サイズの商品
            var product = productWithAxes("色", "サイズ");

            // 操作：バリアントを登録する
            product.addVariant(VariantCode.of(WHITE_M_CODE), WHITE_M, NOW);

            // 確認：販売停止でない
            assertThat(product.variants().getFirst().salesSuspended()).isFalse();
        }

        @Test
        @DisplayName("登録すると、バリアント登録のイベントが登録される")
        void registersVariantRegisteredEvent() {
            // 準備：軸が色・サイズの商品
            var product = productWithAxes("色", "サイズ");

            // 操作：バリアントを 2 つ登録する
            var first = product.addVariant(VariantCode.of(WHITE_M_CODE), WHITE_M, NOW);
            var second = product.addVariant(VariantCode.of(BLACK_M_CODE), BLACK_M, NOW);

            // 確認：登録したバリアントごとに、登録した順にイベントがある
            assertVariantRegisteredEvents(product, first, second);
        }

        @Test
        @DisplayName("AC-20 販売終了の商品にもバリアントを登録できる")
        void addsVariantToDiscontinuedProduct() {
            // 準備：販売終了にした、軸が色・サイズの商品
            var product = productWithAxes("色", "サイズ");
            product.discontinue(NOW);

            // 操作：バリアントを登録する
            var variantId = product.addVariant(VariantCode.of(WHITE_M_CODE), WHITE_M, NOW);

            // 確認：登録したバリアントを持つ
            assertThat(product.variants()).extracting(Variant::id).containsExactly(variantId);
        }

        @Test
        @DisplayName("AC-22 軸の値が軸より少なければ拒否される")
        void rejectsMissingAxisValue() {
            // 準備：軸が色・サイズの商品
            var product = productWithAxes("色", "サイズ");

            // 操作と確認：色だけのバリアントは、業務のエラー INVALID_AXIS_VALUES で拒否される
            assertBusinessError(() -> product.addVariant(VariantCode.of("V1"), List.of("白"), NOW),
                    ProductErrorCode.INVALID_AXIS_VALUES);
        }

        @Test
        @DisplayName("AC-22 軸の値が軸より多ければ拒否される")
        void rejectsExtraAxisValue() {
            // 準備：軸が色・サイズの商品
            var product = productWithAxes("色", "サイズ");

            // 操作と確認：値が 3 つのバリアントは、業務のエラー INVALID_AXIS_VALUES で拒否される
            assertBusinessError(() -> product.addVariant(VariantCode.of("V1"), List.of("白", "M", "綿"), NOW),
                    ProductErrorCode.INVALID_AXIS_VALUES);
        }

        @Test
        @DisplayName("軸の値は 50 文字まで指定できる")
        void acceptsAxisValueUpTo50Characters() {
            // 準備：軸が色の商品
            var product = productWithAxes("色");

            // 操作と確認：50 文字の値で登録できる
            assertThatCode(() -> product.addVariant(VariantCode.of("V1"), List.of("あ".repeat(50)), NOW)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("軸の値が 51 文字以上なら拒否される")
        void rejectsAxisValueOver50Characters() {
            // 準備：軸が色の商品
            var product = productWithAxes("色");

            // 操作と確認：業務のエラー INVALID_AXIS_VALUES で拒否される
            assertBusinessError(() -> product.addVariant(VariantCode.of("V2"), List.of("あ".repeat(51)), NOW),
                    ProductErrorCode.INVALID_AXIS_VALUES);
        }

        @Test
        @DisplayName("軸の値が空白だけなら拒否される")
        void rejectsBlankAxisValue() {
            // 準備：軸が色の商品
            var product = productWithAxes("色");

            // 操作と確認：業務のエラー INVALID_AXIS_VALUES で拒否される
            assertBusinessError(() -> product.addVariant(VariantCode.of("V3"), List.of(" "), NOW),
                    ProductErrorCode.INVALID_AXIS_VALUES);
        }

        @Test
        @DisplayName("AC-23 軸の値の組み合わせが重複すれば拒否される")
        void rejectsDuplicatedAxisValues() {
            // 準備：白・M のバリアントを持つ商品
            var product = productWithAxes("色", "サイズ");
            product.addVariant(VariantCode.of("V1"), WHITE_M, NOW);

            // 操作と確認：同じ白・M は、業務のエラー VARIANT_AXIS_VALUES_ALREADY_EXISTS で拒否される
            assertBusinessError(() -> product.addVariant(VariantCode.of("V2"), WHITE_M, NOW),
                    ProductErrorCode.VARIANT_AXIS_VALUES_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("AC-23 一部の軸の値が同じでも、組み合わせが違えば登録できる")
        void acceptsDifferentCombination() {
            // 準備：白・M のバリアントを持つ商品
            var product = productWithAxes("色", "サイズ");
            product.addVariant(VariantCode.of("V1"), WHITE_M, NOW);

            // 操作と確認：色が同じ白・L は登録できる
            assertThatCode(() -> product.addVariant(VariantCode.of("V3"), List.of("白", "L"), NOW)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-24 軸がなければ、バリアントは 1 つだけ")
        void acceptsOnlyOneVariantWithoutAxes() {
            // 準備：バリアントを 1 つ持つ、軸のない商品
            var product = productWithAxes();
            product.addVariant(VariantCode.of("V1"), List.of(), NOW);

            // 操作と確認：2 つ目は、業務のエラー SINGLE_VARIANT_ONLY で拒否される
            assertBusinessError(() -> product.addVariant(VariantCode.of("V2"), List.of(), NOW),
                    ProductErrorCode.SINGLE_VARIANT_ONLY);
        }

        @Test
        @DisplayName("AC-21 商品内でバリアント品番が重複すれば拒否される")
        void rejectsDuplicatedVariantCode() {
            // 準備：品番 V1 のバリアントを持つ商品
            var product = productWithAxes("色");
            product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);

            // 操作と確認：同じ品番は、軸の値が違っても業務のエラー VARIANT_CODE_ALREADY_EXISTS で拒否される
            assertBusinessError(() -> product.addVariant(VariantCode.of("V1"), List.of("黒"), NOW),
                    ProductErrorCode.VARIANT_CODE_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("バリアントの並び順")
    class ReorderVariants {

        @Test
        @DisplayName("AC-27 送った順に並ぶ")
        void reorders() {
            // 準備：白、黒の順のバリアント
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            var black = product.addVariant(VariantCode.of("V2"), List.of("黒"), NOW);

            // 操作：黒、白の順に並べ替える
            product.reorderVariants(List.of(black, white));

            // 確認：送った順に並ぶ
            assertThat(product.variants()).extracting(Variant::id).containsExactly(black, white);
        }

        @Test
        @DisplayName("AC-27 表示順を、送った順に 1 から振り直す")
        void reassignsDisplayOrders() {
            // 準備：白、黒の順のバリアント
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            var black = product.addVariant(VariantCode.of("V2"), List.of("黒"), NOW);

            // 操作：黒、白の順に並べ替える
            product.reorderVariants(List.of(black, white));

            // 確認：並びの順に 1、2
            assertThat(product.variants()).extracting(Variant::displayOrder).containsExactly(1, 2);
        }

        @Test
        @DisplayName("送ったバリアントが足りなければ拒否される")
        void rejectsMissingVariant() {
            // 準備：白、黒のバリアント
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            product.addVariant(VariantCode.of("V2"), List.of("黒"), NOW);

            // 操作と確認：白だけは、業務のエラー INVALID_VARIANT_ORDER で拒否される
            assertBusinessError(() -> product.reorderVariants(List.of(white)), ProductErrorCode.INVALID_VARIANT_ORDER);
        }

        @Test
        @DisplayName("商品にないバリアントを送れば拒否される")
        void rejectsUnknownVariant() {
            // 準備：白、黒のバリアント
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            var black = product.addVariant(VariantCode.of("V2"), List.of("黒"), NOW);

            // 操作と確認：知らない ID を加えると、業務のエラー INVALID_VARIANT_ORDER で拒否される
            assertBusinessError(() -> product.reorderVariants(List.of(white, black, UUID.randomUUID())),
                    ProductErrorCode.INVALID_VARIANT_ORDER);
        }

        @Test
        @DisplayName("同じバリアントを重ねて送れば拒否される")
        void rejectsDuplicatedVariant() {
            // 準備：白、黒のバリアント
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            product.addVariant(VariantCode.of("V2"), List.of("黒"), NOW);

            // 操作と確認：白を 2 回送ると、業務のエラー INVALID_VARIANT_ORDER で拒否される
            assertBusinessError(() -> product.reorderVariants(List.of(white, white)), ProductErrorCode.INVALID_VARIANT_ORDER);
        }
    }

    @Nested
    @DisplayName("バリアントの販売停止")
    class SuspendVariant {

        @Test
        @DisplayName("AC-28 バリアントを販売停止にできる")
        void suspends() {
            // 準備：販売中のバリアント
            var product = productWithVariant();
            var variantId = product.variants().getFirst().id();

            // 操作：販売停止にする
            var changed = product.suspendVariant(variantId);

            // 確認：販売停止になり、変えたことを返す
            assertThat(product.variants().getFirst().salesSuspended()).isTrue();
            assertThat(changed).isTrue();
        }

        @Test
        @DisplayName("AC-28 バリアントの販売停止を解除できる")
        void resumes() {
            // 準備：販売停止中のバリアント
            var product = productWithVariant();
            var variantId = product.variants().getFirst().id();
            product.suspendVariant(variantId);

            // 操作：販売停止を解除する
            var changed = product.resumeVariant(variantId);

            // 確認：販売中に戻り、変えたことを返す
            assertThat(product.variants().getFirst().salesSuspended()).isFalse();
            assertThat(changed).isTrue();
        }

        @Test
        @DisplayName("AC-28 販売停止にしても、ほかのバリアントは販売中のまま")
        void keepsOtherVariantsOnSale() {
            // 準備：白、黒のバリアント
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V-WH"), List.of("白"), NOW);
            product.addVariant(VariantCode.of("V-BK"), List.of("黒"), NOW);

            // 操作：白を販売停止にする
            product.suspendVariant(white);

            // 確認：白だけが販売停止
            assertThat(product.variants()).extracting(Variant::salesSuspended).containsExactly(true, false);
        }

        @Test
        @DisplayName("AC-28 販売停止にしても、並び順は変わらない")
        void keepsOrder() {
            // 準備：白、黒の順のバリアント
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V-WH"), List.of("白"), NOW);
            var black = product.addVariant(VariantCode.of("V-BK"), List.of("黒"), NOW);

            // 操作：白を販売停止にする
            product.suspendVariant(white);

            // 確認：白、黒の順のまま
            assertThat(product.variants()).extracting(Variant::id).containsExactly(white, black);
        }

        @Test
        @DisplayName("販売中のバリアントの解除は何もせず、変えなかったことを返す")
        void resumeOfOnSaleVariantIsIdempotent() {
            // 準備：販売中のバリアント
            var product = productWithVariant();
            var variantId = product.variants().getFirst().id();

            // 操作：販売停止を解除する
            var changed = product.resumeVariant(variantId);

            // 確認：販売中のままで、変えなかったことを返す
            assertThat(product.variants().getFirst().salesSuspended()).isFalse();
            assertThat(changed).isFalse();
        }

        @Test
        @DisplayName("販売停止中のバリアントの販売停止は何もせず、変えなかったことを返す")
        void suspendOfSuspendedVariantIsIdempotent() {
            // 準備：販売停止中のバリアント
            var product = productWithVariant();
            var variantId = product.variants().getFirst().id();
            product.suspendVariant(variantId);

            // 操作：もう一度販売停止にする
            var changed = product.suspendVariant(variantId);

            // 確認：販売停止のままで、変えなかったことを返す
            assertThat(product.variants().getFirst().salesSuspended()).isTrue();
            assertThat(changed).isFalse();
        }

        @Test
        @DisplayName("販売終了の商品のバリアントも、販売停止にできる")
        void suspendsVariantOfDiscontinuedProduct() {
            // 準備：販売終了の商品
            var product = productWithVariant();
            product.discontinue(NOW);
            var variantId = product.variants().getFirst().id();

            // 操作と確認：販売停止にでき、変えたことを返す
            assertThat(product.suspendVariant(variantId)).isTrue();
        }

        @Test
        @DisplayName("販売終了の商品のバリアントも、販売停止を解除できる")
        void resumesVariantOfDiscontinuedProduct() {
            // 準備：販売停止中のバリアントを持つ、販売終了の商品
            var product = productWithVariant();
            var variantId = product.variants().getFirst().id();
            product.suspendVariant(variantId);
            product.discontinue(NOW);

            // 操作と確認：解除でき、変えたことを返す
            assertThat(product.resumeVariant(variantId)).isTrue();
        }

        @Test
        @DisplayName("商品にないバリアントは、販売停止にできない")
        void rejectsSuspendingUnknownVariant() {
            // 準備：バリアントを 1 つ持つ商品
            var product = productWithVariant();

            // 操作と確認：業務のエラー NOT_FOUND で拒否される
            assertBusinessError(() -> product.suspendVariant(UUID.randomUUID()), CommonErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("商品にないバリアントは、販売停止を解除できない")
        void rejectsResumingUnknownVariant() {
            // 準備：バリアントを 1 つ持つ商品
            var product = productWithVariant();

            // 操作と確認：業務のエラー NOT_FOUND で拒否される
            assertBusinessError(() -> product.resumeVariant(UUID.randomUUID()), CommonErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("販売停止と解除では、イベントは登録されない")
        void registersNoEvent() {
            // 準備：販売中のバリアント
            var product = productWithVariant();
            var variantId = product.variants().getFirst().id();

            // 操作：販売停止にし、解除する
            product.suspendVariant(variantId);
            product.resumeVariant(variantId);

            // 確認：イベントがない
            assertThat(product.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("バリアントの削除")
    class DeleteVariant {

        @Test
        @DisplayName("AC-71 C 側に表示されていない商品のバリアントは削除できる")
        void deletesVariantOfNeverShownProduct() {
            // 準備：非公開の商品のバリアント
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);

            // 操作：削除する
            product.deleteVariant(white, NOW);

            // 確認：バリアントがなくなる
            assertThat(product.variants()).isEmpty();
        }

        @Test
        @DisplayName("バリアントを削除すると、バリアント削除のイベントが登録される")
        void registersVariantDeletedEvent() {
            // 準備：非公開の商品のバリアント（登録のイベントは捨てる）
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            product.pullEvents();

            // 操作：削除する
            product.deleteVariant(white, NOW);

            // 確認：削除したバリアントのイベントだけが登録されている
            assertThat(product.pullEvents()).singleElement()
                    .isInstanceOfSatisfying(VariantDeleted.class, event -> assertThat(event.variantId()).isEqualTo(white));
        }

        @Test
        @DisplayName("AC-71 C 側に表示されたことのある商品のバリアントは削除できない")
        void rejectsDeletingVariantOfShownProduct() {
            // 準備：バリアントが 2 つの、公開して表示された商品
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            product.addVariant(VariantCode.of("V2"), List.of("黒"), NOW);
            product.publish(NOW);

            // 操作と確認：業務のエラー PRODUCT_ALREADY_SHOWN で拒否される
            assertBusinessError(() -> product.deleteVariant(white, NOW), ProductErrorCode.PRODUCT_ALREADY_SHOWN);
        }

        @Test
        @DisplayName("公開中（表示予定）の商品の最後のバリアントは削除できない")
        void rejectsDeletingLastVariantOfPublishedProduct() {
            // 準備：公開開始日時が明日の、公開中の商品
            var product = productWithVariant();
            product.changePublicationPeriod(PublicationPeriod.of(NOW.plus(ONE_DAY), null), NOW);
            product.publish(NOW);
            var variantId = product.variants().getFirst().id();

            // 操作と確認：業務のエラー PRODUCT_HAS_NO_VARIANTS で拒否される
            assertBusinessError(() -> product.deleteVariant(variantId, NOW), ProductErrorCode.PRODUCT_HAS_NO_VARIANTS);
        }

        @Test
        @DisplayName("商品にないバリアントは削除できない")
        void rejectsUnknownVariant() {
            // 準備：バリアントを 1 つ持つ商品
            var product = productWithVariant();

            // 操作と確認：業務のエラー NOT_FOUND で拒否される
            assertBusinessError(() -> product.deleteVariant(UUID.randomUUID(), NOW), CommonErrorCode.NOT_FOUND);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * バリアント登録のイベントが、登録した 2 つのバリアントの分だけ、登録した順にあることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>登録されたイベントが 2 件</li>
     *   <li>1 件目の種類が {@link VariantRegistered} で、商品 ID がこの商品、バリアント ID が 1 つ目</li>
     *   <li>2 件目の種類が {@link VariantRegistered} で、商品 ID がこの商品、バリアント ID が 2 つ目</li>
     * </ul>
     *
     * @param product 確認する商品
     * @param first   1 つ目に登録したバリアントの ID（集約が振るので、定数にできない）
     * @param second  2 つ目に登録したバリアントの ID（集約が振るので、定数にできない）
     */
    private static void assertVariantRegisteredEvents(Product product, UUID first, UUID second) {
        var events = product.pullEvents();
        assertThat(events).hasSize(2);
        assertThat(events.getFirst()).isInstanceOfSatisfying(VariantRegistered.class, event -> {
            assertThat(event.productId()).isEqualTo(product.id());
            assertThat(event.variantId()).isEqualTo(first);
        });
        assertThat(events.get(1)).isInstanceOfSatisfying(VariantRegistered.class, event -> {
            assertThat(event.productId()).isEqualTo(product.id());
            assertThat(event.variantId()).isEqualTo(second);
        });
    }

    // ============================== 準備の補助 ==============================

    /**
     * 軸を指定して、非公開の商品を作る。
     *
     * @param axes 軸の名前
     * @return 非公開の商品（バリアントなし）
     */
    private Product productWithAxes(String... axes) {
        return factory.create(ProductCode.of("ABC-001"), "T シャツ", "", TAX_CATEGORY_ID, PriceExclTax.of(1000),
                Set.of(), List.of(axes));
    }

    /**
     * 軸がなく、販売中のバリアントを 1 つ持つ非公開の商品を作る。
     *
     * <p>バリアント登録のイベントは捨てておく。
     *
     * @return 非公開の商品
     */
    private Product productWithVariant() {
        var product = productWithAxes();
        product.addVariant(VariantCode.of("ABC-001"), List.of(), NOW);
        product.pullEvents();
        return product;
    }
}
