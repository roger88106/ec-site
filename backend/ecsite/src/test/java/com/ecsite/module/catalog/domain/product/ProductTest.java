package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.PublicationPeriod;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import com.ecsite.module.catalog.event.ProductDeleted;
import com.ecsite.module.catalog.event.ProductDiscontinued;
import com.ecsite.module.catalog.event.ProductPriceChanged;
import com.ecsite.module.catalog.event.ProductPublished;
import com.ecsite.module.catalog.event.ProductUnpublished;
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
 * 商品（集約）の単体テスト（T304）。<br />
 * 現在時刻は引数で固定する。
 */
class ProductTest {

    /**
     * 現在：2026-10-08 00:00（日本時間）
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 1 日
     */
    private static final Duration ONE_DAY = Duration.ofDays(1);

    /**
     * 税区分 ID（税モジュールの ID なので、存在しなくてよい）
     */
    private static final UUID TAX_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * テストで使う ID
     */
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000001001");

    private final ProductFactory factory = new ProductFactory();

    @Nested
    @DisplayName("登録")
    class Create {

        @Test
        @DisplayName("AC-13、17 指定した内容で登録され、登録直後は非公開")
        void createsUnpublished() {
            var product = factory.create(ProductCode.of("ABC-001"), "T シャツ", "綿 100%", TAX_CATEGORY_ID,
                    PriceExclTax.of(1000), Set.of(CATEGORY_ID), List.of("色", "サイズ"));

            assertThat(product.id()).isNotNull();
            assertThat(product.productCode()).isEqualTo(ProductCode.of("ABC-001"));
            assertThat(product.name()).isEqualTo("T シャツ");
            assertThat(product.description()).isEqualTo("綿 100%");
            assertThat(product.taxCategoryId()).isEqualTo(TAX_CATEGORY_ID);
            assertThat(product.priceExclTax()).isEqualTo(PriceExclTax.of(1000));
            assertThat(product.categoryIds()).containsExactly(CATEGORY_ID);
            assertThat(product.axes()).containsExactly("色", "サイズ");
            assertThat(product.status()).isEqualTo(PublicationStatus.UNPUBLISHED);
            assertThat(product.firstShownAt()).isNull();
            assertThat(product.variants()).isEmpty();
            assertThat(product.version()).isZero();
        }

        @Test
        @DisplayName("AC-13 軸は 0〜3 つ。4 つ以上は拒否される")
        void acceptsUpToThreeAxes() {
            assertThatCode(() -> productWithAxes()).doesNotThrowAnyException();
            assertThatCode(() -> productWithAxes("色", "サイズ", "素材")).doesNotThrowAnyException();
            assertBusinessError(() -> productWithAxes("色", "サイズ", "素材", "柄"), ProductErrorCode.INVALID_VARIANT_AXES);
        }

        @Test
        @DisplayName("軸の名前は 1〜30 文字で、重複できない")
        void rejectsInvalidAxisName() {
            assertThatCode(() -> productWithAxes("あ".repeat(30))).doesNotThrowAnyException();
            assertBusinessError(() -> productWithAxes("あ".repeat(31)), ProductErrorCode.INVALID_VARIANT_AXES);
            assertBusinessError(() -> productWithAxes(" "), ProductErrorCode.INVALID_VARIANT_AXES);
            assertBusinessError(() -> productWithAxes("色", "色"), ProductErrorCode.INVALID_VARIANT_AXES);
        }

        @Test
        @DisplayName("商品名は 1〜200 文字で、空白だけは拒否される")
        void rejectsInvalidName() {
            assertThatCode(() -> productNamed("あ".repeat(200))).doesNotThrowAnyException();
            assertBusinessError(() -> productNamed("あ".repeat(201)), ProductErrorCode.INVALID_PRODUCT_NAME);
            assertBusinessError(() -> productNamed(" "), ProductErrorCode.INVALID_PRODUCT_NAME);
        }

        @Test
        @DisplayName("説明は空でもよく、10,000 文字まで")
        void acceptsDescriptionUpTo10000Characters() {
            assertThatCode(() -> productDescribed("")).doesNotThrowAnyException();
            assertThatCode(() -> productDescribed("あ".repeat(10_000))).doesNotThrowAnyException();
            assertBusinessError(() -> productDescribed("あ".repeat(10_001)), ProductErrorCode.INVALID_PRODUCT_DESCRIPTION);
        }
    }

    @Nested
    @DisplayName("編集")
    class Update {

        @Test
        @DisplayName("AC-18、25 商品名、説明、税区分、税抜価格、所属カテゴリを変更でき、価格が変わればイベントが登録される")
        void updates() {
            var product = productWithAxes();
            var newTaxCategoryId = UUID.randomUUID();
            var newCategoryId = UUID.randomUUID();

            product.update("ポロシャツ", "綿 50%", newTaxCategoryId, PriceExclTax.of(2000), Set.of(newCategoryId), NOW);

            assertThat(product.name()).isEqualTo("ポロシャツ");
            assertThat(product.description()).isEqualTo("綿 50%");
            assertThat(product.taxCategoryId()).isEqualTo(newTaxCategoryId);
            assertThat(product.priceExclTax()).isEqualTo(PriceExclTax.of(2000));
            assertThat(product.categoryIds()).containsExactly(newCategoryId);
            assertThat(product.pullEvents()).singleElement()
                    .isInstanceOfSatisfying(ProductPriceChanged.class, event -> {
                        assertThat(event.productId()).isEqualTo(product.id());
                        assertThat(event.priceExclTax()).isEqualTo(Money.of(2000));
                        assertThat(event.occurredAt()).isEqualTo(NOW);
                    });
        }

        @Test
        @DisplayName("価格が変わらなければ、価格変更のイベントは登録されない")
        void doesNotRegisterEventWithoutPriceChange() {
            var product = productWithAxes();

            product.update("ポロシャツ", "", TAX_CATEGORY_ID, product.priceExclTax(), Set.of(), NOW);

            assertThat(product.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("変更後の商品名も検証され、拒否されたら何も変わらない")
        void rejectsInvalidName() {
            var product = productWithAxes();

            assertBusinessError(() -> product.update(" ", "", TAX_CATEGORY_ID, PriceExclTax.of(2000), Set.of(), NOW),
                    ProductErrorCode.INVALID_PRODUCT_NAME);
            assertThat(product.priceExclTax()).isEqualTo(PriceExclTax.of(1000));
        }
    }

    @Nested
    @DisplayName("バリアントの登録")
    class AddVariant {

        @Test
        @DisplayName("AC-20 品番と軸の値で登録でき、並びの最後に加わり、イベントが登録される")
        void addsVariant() {
            var product = productWithAxes("色", "サイズ");

            var first = product.addVariant(VariantCode.of("ABC-001-WH-M"), List.of("白", "M"), NOW);
            var second = product.addVariant(VariantCode.of("ABC-001-BK-M"), List.of("黒", "M"), NOW);

            assertThat(product.variants()).extracting(Variant::id).containsExactly(first, second);
            assertThat(product.variants().getFirst().axisValues()).containsExactly("白", "M");
            assertThat(product.variants().getFirst().salesSuspended()).isFalse();
            assertThat(product.pullEvents()).hasSize(2).first()
                    .isInstanceOfSatisfying(VariantRegistered.class, event -> {
                        assertThat(event.productId()).isEqualTo(product.id());
                        assertThat(event.variantId()).isEqualTo(first);
                    });
        }

        @Test
        @DisplayName("AC-22 商品のすべての軸の値を持たなければ拒否される")
        void rejectsMissingAxisValue() {
            var product = productWithAxes("色", "サイズ");

            assertBusinessError(() -> product.addVariant(VariantCode.of("V1"), List.of("白"), NOW),
                    ProductErrorCode.INVALID_AXIS_VALUES);
            assertBusinessError(() -> product.addVariant(VariantCode.of("V1"), List.of("白", "M", "綿"), NOW),
                    ProductErrorCode.INVALID_AXIS_VALUES);
        }

        @Test
        @DisplayName("軸の値は 1〜50 文字")
        void rejectsInvalidAxisValue() {
            var product = productWithAxes("色");

            assertThatCode(() -> product.addVariant(VariantCode.of("V1"), List.of("あ".repeat(50)), NOW)).doesNotThrowAnyException();
            assertBusinessError(() -> product.addVariant(VariantCode.of("V2"), List.of("あ".repeat(51)), NOW),
                    ProductErrorCode.INVALID_AXIS_VALUES);
            assertBusinessError(() -> product.addVariant(VariantCode.of("V3"), List.of(" "), NOW),
                    ProductErrorCode.INVALID_AXIS_VALUES);
        }

        @Test
        @DisplayName("AC-23 軸の値の組み合わせが重複すれば拒否される")
        void rejectsDuplicatedAxisValues() {
            var product = productWithAxes("色", "サイズ");
            product.addVariant(VariantCode.of("V1"), List.of("白", "M"), NOW);

            assertBusinessError(() -> product.addVariant(VariantCode.of("V2"), List.of("白", "M"), NOW),
                    ProductErrorCode.VARIANT_AXIS_VALUES_ALREADY_EXISTS);
            assertThatCode(() -> product.addVariant(VariantCode.of("V3"), List.of("白", "L"), NOW)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-24 軸がなければ、バリアントは 1 つだけ")
        void acceptsOnlyOneVariantWithoutAxes() {
            var product = productWithAxes();
            product.addVariant(VariantCode.of("V1"), List.of(), NOW);

            assertBusinessError(() -> product.addVariant(VariantCode.of("V2"), List.of(), NOW),
                    ProductErrorCode.SINGLE_VARIANT_ONLY);
        }

        @Test
        @DisplayName("AC-21 商品内でバリアント品番が重複すれば拒否される")
        void rejectsDuplicatedVariantCode() {
            var product = productWithAxes("色");
            product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);

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
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            var black = product.addVariant(VariantCode.of("V2"), List.of("黒"), NOW);

            product.reorderVariants(List.of(black, white));

            assertThat(product.variants()).extracting(Variant::id).containsExactly(black, white);
            assertThat(product.variants()).extracting(Variant::displayOrder).containsExactly(1, 2);
        }

        @Test
        @DisplayName("過不足・重複があれば拒否される")
        void rejectsMismatch() {
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            var black = product.addVariant(VariantCode.of("V2"), List.of("黒"), NOW);

            assertBusinessError(() -> product.reorderVariants(List.of(white)), ProductErrorCode.INVALID_VARIANT_ORDER);
            assertBusinessError(() -> product.reorderVariants(List.of(white, black, UUID.randomUUID())),
                    ProductErrorCode.INVALID_VARIANT_ORDER);
            assertBusinessError(() -> product.reorderVariants(List.of(white, white)), ProductErrorCode.INVALID_VARIANT_ORDER);
        }
    }

    @Nested
    @DisplayName("状態の切り替え")
    class Status {

        @Test
        @DisplayName("AC-29 公開でき、初回表示日時が記録され、イベントが登録される")
        void publishes() {
            var product = productWithVariant();

            product.publish(NOW);

            assertThat(product.status()).isEqualTo(PublicationStatus.PUBLISHED);
            assertThat(product.firstShownAt()).isEqualTo(NOW);
            assertThat(product.isVisibleAt(NOW)).isTrue();
            assertThat(product.pullEvents()).singleElement().isInstanceOf(ProductPublished.class);
        }

        @Test
        @DisplayName("AC-31 バリアントのない商品は公開できない")
        void rejectsPublishingWithoutVariants() {
            var product = productWithAxes();

            assertBusinessError(() -> product.publish(NOW), ProductErrorCode.PRODUCT_HAS_NO_VARIANTS);
            assertThat(product.status()).isEqualTo(PublicationStatus.UNPUBLISHED);
        }

        @Test
        @DisplayName("AC-30 非公開にでき、イベントが登録される")
        void unpublishes() {
            var product = productWithVariant();
            product.publish(NOW);
            product.pullEvents();

            product.unpublish(NOW.plus(ONE_DAY));

            assertThat(product.status()).isEqualTo(PublicationStatus.UNPUBLISHED);
            assertThat(product.isVisibleAt(NOW.plus(ONE_DAY))).isFalse();
            assertThat(product.pullEvents()).singleElement().isInstanceOf(ProductUnpublished.class);
        }

        @Test
        @DisplayName("同じ状態への切り替えは何もせず、イベントも登録されない")
        void isIdempotent() {
            var product = productWithVariant();

            product.unpublish(NOW);
            assertThat(product.pullEvents()).isEmpty();

            product.publish(NOW);
            product.pullEvents();
            product.publish(NOW.plus(ONE_DAY));
            assertThat(product.firstShownAt()).isEqualTo(NOW);
            assertThat(product.pullEvents()).isEmpty();

            product.discontinue(NOW);
            product.pullEvents();
            product.discontinue(NOW);
            assertThat(product.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("AC-32 販売終了にでき、販売終了からは公開にも非公開にも戻せない")
        void discontinues() {
            var product = productWithVariant();
            product.publish(NOW);
            product.pullEvents();

            product.discontinue(NOW);

            assertThat(product.status()).isEqualTo(PublicationStatus.DISCONTINUED);
            assertThat(product.isVisibleAt(NOW)).isFalse();
            assertThat(product.pullEvents()).singleElement().isInstanceOf(ProductDiscontinued.class);
            assertBusinessError(() -> product.publish(NOW), ProductErrorCode.PRODUCT_DISCONTINUED);
            assertBusinessError(() -> product.unpublish(NOW), ProductErrorCode.PRODUCT_DISCONTINUED);
        }

        @Test
        @DisplayName("非公開の商品も販売終了にできる")
        void discontinuesUnpublished() {
            var product = productWithAxes();

            product.discontinue(NOW);

            assertThat(product.status()).isEqualTo(PublicationStatus.DISCONTINUED);
        }
    }

    @Nested
    @DisplayName("初回表示日時")
    class FirstShownAt {

        @Test
        @DisplayName("AC-54 過去になった初回表示日時は、再公開しても変わらない")
        void keepsFirstShownAtAfterRepublishing() {
            var product = productWithVariant();
            product.publish(NOW);
            product.unpublish(NOW.plus(ONE_DAY));

            product.publish(NOW.plus(ONE_DAY.multipliedBy(2)));

            assertThat(product.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("公開開始日時が未来なら、初回表示日時は公開開始日時になり、表示前に非公開にすると空に戻る")
        void clearsFutureFirstShownAtWhenUnpublished() {
            var start = NOW.plus(ONE_DAY);
            var product = productWithPeriod(PublicationPeriod.of(start, null));

            product.publish(NOW);
            assertThat(product.firstShownAt()).isEqualTo(start);
            assertThat(product.isVisibleAt(NOW)).isFalse();
            assertThat(product.isVisibleAt(start)).isTrue();

            product.unpublish(NOW);
            assertThat(product.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("表示前に販売終了にしても、初回表示日時は空に戻る")
        void clearsFutureFirstShownAtWhenDiscontinued() {
            var product = productWithPeriod(PublicationPeriod.of(NOW.plus(ONE_DAY), null));
            product.publish(NOW);

            product.discontinue(NOW);

            assertThat(product.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("公開終了日時を過ぎていれば表示されないので、初回表示日時は空のまま")
        void leavesFirstShownAtEmptyAfterPublishEnd() {
            var product = productWithPeriod(PublicationPeriod.of(null, NOW));

            product.publish(NOW);

            assertThat(product.firstShownAt()).isNull();
        }
    }

    @Nested
    @DisplayName("公開期間")
    class ChangePublicationPeriod {

        @Test
        @DisplayName("AC-34 公開開始・終了日時を設定でき、どちらも空にできる")
        void setsPeriod() {
            var product = productWithVariant();
            var period = PublicationPeriod.of(NOW.plus(ONE_DAY), NOW.plus(ONE_DAY.multipliedBy(10)));

            product.changePublicationPeriod(period, NOW);
            assertThat(product.publicationPeriod()).isEqualTo(period);

            product.changePublicationPeriod(PublicationPeriod.UNLIMITED, NOW);
            assertThat(product.publicationPeriod()).isEqualTo(PublicationPeriod.UNLIMITED);
        }

        @Test
        @DisplayName("AC-36 終了日時は開始日時より後。同じか前なら拒否される")
        void rejectsEndNotAfterStart() {
            assertBusinessError(() -> PublicationPeriod.of(NOW, NOW), ProductErrorCode.INVALID_PUBLICATION_PERIOD);
            assertBusinessError(() -> PublicationPeriod.of(NOW, NOW.minus(ONE_DAY)),
                    ProductErrorCode.INVALID_PUBLICATION_PERIOD);
            assertThatCode(() -> PublicationPeriod.of(NOW, NOW.plusSeconds(1))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-35 公開中でも、開始日時より前と終了日時以降は C 側に表示されない")
        void isVisibleOnlyWithinPeriod() {
            var start = NOW.plus(ONE_DAY);
            var end = NOW.plus(ONE_DAY.multipliedBy(2));
            var product = productWithVariant();
            product.changePublicationPeriod(PublicationPeriod.of(start, end), NOW);
            product.publish(NOW);

            assertThat(product.isVisibleAt(start.minusSeconds(1))).isFalse();
            assertThat(product.isVisibleAt(start)).isTrue();
            assertThat(product.isVisibleAt(end.minusSeconds(1))).isTrue();
            assertThat(product.isVisibleAt(end)).isFalse();
        }

        @Test
        @DisplayName("公開中に開始日時を変えると、表示前の初回表示日時を計算し直す")
        void recalculatesScheduledFirstShownAt() {
            var product = productWithVariant();
            product.changePublicationPeriod(PublicationPeriod.of(NOW.plus(ONE_DAY), null), NOW);
            product.publish(NOW);
            assertThat(product.firstShownAt()).isEqualTo(NOW.plus(ONE_DAY));

            var later = NOW.plus(ONE_DAY.multipliedBy(3));
            product.changePublicationPeriod(PublicationPeriod.of(later, null), NOW);
            assertThat(product.firstShownAt()).isEqualTo(later);

            // 開始を空にすると、変えた時刻から表示される
            var changedAt = NOW.plusSeconds(60);
            product.changePublicationPeriod(PublicationPeriod.UNLIMITED, changedAt);
            assertThat(product.firstShownAt()).isEqualTo(changedAt);
        }

        @Test
        @DisplayName("AC-54 表示されて確定した初回表示日時は、公開期間を変えても変わらない")
        void keepsConfirmedFirstShownAt() {
            var product = productWithVariant();
            product.publish(NOW);

            product.changePublicationPeriod(PublicationPeriod.of(NOW.plus(ONE_DAY), null), NOW.plusSeconds(60));

            assertThat(product.firstShownAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("公開中に終了日時を過ぎた期間にすると、表示されないので初回表示日時は空になる")
        void clearsFirstShownAtWhenPeriodHasEnded() {
            var product = productWithVariant();
            product.changePublicationPeriod(PublicationPeriod.of(NOW.plus(ONE_DAY), null), NOW);
            product.publish(NOW);

            product.changePublicationPeriod(PublicationPeriod.of(null, NOW), NOW);

            assertThat(product.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("公開中でない商品の期間を変えても、初回表示日時は空のまま")
        void leavesFirstShownAtOfUnpublished() {
            var product = productWithVariant();

            product.changePublicationPeriod(PublicationPeriod.of(NOW.plus(ONE_DAY), null), NOW);

            assertThat(product.firstShownAt()).isNull();
        }

        @Test
        @DisplayName("AC-33 公開を予約し、開始日時の前に取り消した（非公開にした）商品は削除できる")
        void deletesCancelledReservation() {
            var product = productWithVariant();
            product.changePublicationPeriod(PublicationPeriod.of(NOW.plus(ONE_DAY), null), NOW);
            product.publish(NOW);

            product.unpublish(NOW.plusSeconds(60));

            assertThatCode(() -> product.delete(NOW.plusSeconds(60))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("イベントは登録されない")
        void registersNoEvent() {
            var product = productWithVariant();

            product.changePublicationPeriod(PublicationPeriod.of(NOW.plus(ONE_DAY), null), NOW);

            assertThat(product.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("商品画像")
    class Images {

        @Test
        @DisplayName("AC-37 画像は並びの最後に加わり、先頭が代表画像になる")
        void addsImagesInOrder() {
            var product = productWithVariant();
            var first = UUID.randomUUID();
            var second = UUID.randomUUID();
            var firstAsset = UUID.randomUUID();
            var secondAsset = UUID.randomUUID();

            product.addImage(first, firstAsset);
            product.addImage(second, secondAsset);

            assertThat(product.images()).extracting(ProductImage::id).containsExactly(first, second);
            assertThat(product.images()).extracting(ProductImage::assetId).containsExactly(firstAsset, secondAsset);
            assertThat(product.representativeImage()).map(ProductImage::id).contains(first);
        }

        @Test
        @DisplayName("画像がなければ代表画像もない")
        void hasNoRepresentativeImageWithoutImages() {
            assertThat(productWithVariant().representativeImage()).isEmpty();
        }

        @Test
        @DisplayName("AC-40 画像は 1 商品あたり 20 枚まで。21 枚目は拒否される")
        void acceptsUpToTwentyImages() {
            var product = productWithImages(20);

            assertBusinessError(() -> product.addImage(UUID.randomUUID(), UUID.randomUUID()),
                    ProductErrorCode.TOO_MANY_PRODUCT_IMAGES);
            assertThat(product.images()).hasSize(20);
        }

        @Test
        @DisplayName("AC-40 枚数の上限に達しているかを、受付の前に確かめられる")
        void verifiesCapacityBeforeUpload() {
            assertThatCode(() -> productWithImages(19).verifyCanAddImage()).doesNotThrowAnyException();
            assertBusinessError(() -> productWithImages(20).verifyCanAddImage(), ProductErrorCode.TOO_MANY_PRODUCT_IMAGES);
        }

        @Test
        @DisplayName("AC-37 送った順に並べ替えられ、先頭が代表画像になる。過不足・重複があれば拒否される")
        void reordersImages() {
            var product = productWithImages(3);
            var ids = product.images().stream().map(ProductImage::id).toList();

            product.reorderImages(List.of(ids.get(2), ids.get(0), ids.get(1)));

            assertThat(product.images()).extracting(ProductImage::id).containsExactly(ids.get(2), ids.get(0), ids.get(1));
            assertThat(product.representativeImage()).map(ProductImage::id).contains(ids.get(2));
            assertBusinessError(() -> product.reorderImages(List.of(ids.get(0), ids.get(1))),
                    ProductErrorCode.INVALID_IMAGE_ORDER);
            assertBusinessError(() -> product.reorderImages(List.of(ids.get(0), ids.get(0), ids.get(1))),
                    ProductErrorCode.INVALID_IMAGE_ORDER);
            assertBusinessError(() -> product.reorderImages(List.of(ids.get(0), ids.get(1), UUID.randomUUID())),
                    ProductErrorCode.INVALID_IMAGE_ORDER);
        }

        @Test
        @DisplayName("AC-38 バリアントにその商品の画像を 1 枚指定でき、指定を外せる")
        void setsVariantImage() {
            var product = productWithImages(2);
            var variantId = product.variants().getFirst().id();
            var imageId = product.images().get(1).id();

            product.setVariantImage(variantId, imageId);
            assertThat(product.variants().getFirst().imageId()).isEqualTo(imageId);

            product.setVariantImage(variantId, null);
            assertThat(product.variants().getFirst().imageId()).isNull();
        }

        @Test
        @DisplayName("AC-38 その商品にない画像は、バリアントに指定できない。商品にないバリアントは見つからない")
        void rejectsForeignImage() {
            var product = productWithImages(1);
            var variantId = product.variants().getFirst().id();

            assertBusinessError(() -> product.setVariantImage(variantId, UUID.randomUUID()),
                    ProductErrorCode.INVALID_VARIANT_IMAGE);
            assertBusinessError(() -> product.setVariantImage(UUID.randomUUID(), product.images().getFirst().id()),
                    CommonErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("画像を削除すると並びから消え、指定していたバリアントの指定が外れ、手放すアセット ID が返る")
        void deletesImage() {
            var product = productWithImages(2);
            var variantId = product.variants().getFirst().id();
            var deleted = product.images().getFirst();
            product.setVariantImage(variantId, deleted.id());

            var released = product.deleteImage(deleted.id());

            assertThat(product.images()).extracting(ProductImage::id).doesNotContain(deleted.id()).hasSize(1);
            assertThat(product.variants().getFirst().imageId()).isNull();
            assertThat(released).isEqualTo(deleted.assetId());
            assertThat(product.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("C 側に表示されたことのある商品の画像も削除できる。商品にない画像は見つからない")
        void deletesImageOfShownProduct() {
            var product = productWithImages(1);
            product.publish(NOW);

            assertThatCode(() -> product.deleteImage(product.images().getFirst().id())).doesNotThrowAnyException();
            assertBusinessError(() -> product.deleteImage(UUID.randomUUID()), CommonErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("画像は復元できる（スナップショットに含まれる）")
        void restoresImages() {
            var product = productWithImages(2);
            product.setVariantImage(product.variants().getFirst().id(), product.images().get(1).id());

            var restored = factory.restore(product.toSnapshot());

            assertThat(restored.toSnapshot()).isEqualTo(product.toSnapshot());
        }

        /**
         * バリアントが 1 つで、画像を count 枚持つ非公開の商品
         */
        private Product productWithImages(int count) {
            var product = productWithVariant();
            for (int i = 1; i <= count; i++) {
                product.addImage(UUID.randomUUID(), UUID.randomUUID());
            }
            return product;
        }
    }

    @Nested
    @DisplayName("バリアントの販売停止")
    class SuspendVariant {

        @Test
        @DisplayName("AC-28 バリアントを販売停止にでき、解除できる")
        void suspendsAndResumes() {
            var product = productWithVariant();
            var variantId = product.variants().getFirst().id();

            assertThat(product.suspendVariant(variantId)).isTrue();
            assertThat(product.variants().getFirst().salesSuspended()).isTrue();

            assertThat(product.resumeVariant(variantId)).isTrue();
            assertThat(product.variants().getFirst().salesSuspended()).isFalse();
        }

        @Test
        @DisplayName("AC-28 販売停止にしても、ほかのバリアントと並び順は変わらない")
        void keepsOtherVariants() {
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V-WH"), List.of("白"), NOW);
            var black = product.addVariant(VariantCode.of("V-BK"), List.of("黒"), NOW);

            product.suspendVariant(white);

            assertThat(product.variants()).extracting(Variant::id).containsExactly(white, black);
            assertThat(product.variants()).extracting(Variant::salesSuspended).containsExactly(true, false);
        }

        @Test
        @DisplayName("同じ状態への切り替えは何もしない（冪等）")
        void isIdempotent() {
            var product = productWithVariant();
            var variantId = product.variants().getFirst().id();

            assertThat(product.resumeVariant(variantId)).isFalse();
            product.suspendVariant(variantId);
            assertThat(product.suspendVariant(variantId)).isFalse();
            assertThat(product.variants().getFirst().salesSuspended()).isTrue();
        }

        @Test
        @DisplayName("販売終了の商品のバリアントも、販売停止・解除できる")
        void suspendsVariantOfDiscontinuedProduct() {
            var product = productWithVariant();
            product.discontinue(NOW);
            var variantId = product.variants().getFirst().id();

            assertThat(product.suspendVariant(variantId)).isTrue();
        }

        @Test
        @DisplayName("商品にないバリアントは見つからない")
        void rejectsUnknownVariant() {
            var product = productWithVariant();

            assertBusinessError(() -> product.suspendVariant(UUID.randomUUID()), CommonErrorCode.NOT_FOUND);
            assertBusinessError(() -> product.resumeVariant(UUID.randomUUID()), CommonErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("イベントは登録されない")
        void registersNoEvent() {
            var product = productWithVariant();
            var variantId = product.variants().getFirst().id();

            product.suspendVariant(variantId);
            product.resumeVariant(variantId);

            assertThat(product.pullEvents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("削除")
    class Delete {

        @Test
        @DisplayName("AC-33 C 側に一度も表示されていない商品は削除でき、イベントが登録される")
        void deletesNeverShownProduct() {
            var product = productWithVariant();

            product.delete(NOW);

            assertThat(product.pullEvents()).last()
                    .isInstanceOfSatisfying(ProductDeleted.class, event -> assertThat(event.productId()).isEqualTo(product.id()));
        }

        @Test
        @DisplayName("AC-33 表示されたことのある商品は、非公開や販売終了にしても削除できない")
        void rejectsDeletingShownProduct() {
            var product = productWithVariant();
            product.publish(NOW);
            product.discontinue(NOW.plus(ONE_DAY));

            assertBusinessError(() -> product.delete(NOW.plus(ONE_DAY)), ProductErrorCode.PRODUCT_ALREADY_SHOWN);
        }

        @Test
        @DisplayName("AC-33 初回表示日時が未来（表示予定）の商品は削除できる")
        void deletesScheduledProduct() {
            var product = productWithPeriod(PublicationPeriod.of(NOW.plus(ONE_DAY), null));
            product.publish(NOW);

            assertThatCode(() -> product.delete(NOW)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-71 表示されていない商品のバリアントは削除でき、イベントが登録される")
        void deletesVariantOfNeverShownProduct() {
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            product.pullEvents();

            product.deleteVariant(white, NOW);

            assertThat(product.variants()).isEmpty();
            assertThat(product.pullEvents()).singleElement()
                    .isInstanceOfSatisfying(VariantDeleted.class, event -> assertThat(event.variantId()).isEqualTo(white));
        }

        @Test
        @DisplayName("AC-71 表示されたことのある商品のバリアントは削除できない")
        void rejectsDeletingVariantOfShownProduct() {
            var product = productWithAxes("色");
            var white = product.addVariant(VariantCode.of("V1"), List.of("白"), NOW);
            product.addVariant(VariantCode.of("V2"), List.of("黒"), NOW);
            product.publish(NOW);

            assertBusinessError(() -> product.deleteVariant(white, NOW), ProductErrorCode.PRODUCT_ALREADY_SHOWN);
        }

        @Test
        @DisplayName("公開中（表示予定）の商品の最後のバリアントは削除できない")
        void rejectsDeletingLastVariantOfPublishedProduct() {
            var product = productWithPeriod(PublicationPeriod.of(NOW.plus(ONE_DAY), null));
            product.publish(NOW);
            var variantId = product.variants().getFirst().id();

            assertBusinessError(() -> product.deleteVariant(variantId, NOW), ProductErrorCode.PRODUCT_HAS_NO_VARIANTS);
        }

        @Test
        @DisplayName("商品にないバリアントは見つからない")
        void rejectsUnknownVariant() {
            var product = productWithVariant();

            assertBusinessError(() -> product.deleteVariant(UUID.randomUUID(), NOW), CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("復元")
    class Restore {

        @Test
        @DisplayName("スナップショットから復元できる")
        void restores() {
            var product = productWithAxes("色", "サイズ");
            product.addVariant(VariantCode.of("V1"), List.of("白", "M"), NOW);
            product.publish(NOW);

            var restored = factory.restore(product.toSnapshot());

            assertThat(restored.toSnapshot()).isEqualTo(product.toSnapshot());
            assertThat(restored.pullEvents()).isEmpty();
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
     * 商品名を指定して、軸のない商品を作る（商品名の検証のため）
     */
    private Product productNamed(String name) {
        return factory.create(ProductCode.of("ABC-001"), name, "", TAX_CATEGORY_ID, PriceExclTax.of(1000), Set.of(), List.of());
    }

    /**
     * 説明を指定して、軸のない商品を作る（説明の検証のため）
     */
    private Product productDescribed(String description) {
        return factory.create(ProductCode.of("ABC-001"), "T シャツ", description, TAX_CATEGORY_ID, PriceExclTax.of(1000),
                Set.of(), List.of());
    }

    /**
     * 軸がなく、バリアントを 1 つ持つ非公開の商品
     */
    private Product productWithVariant() {
        var product = productWithAxes();
        product.addVariant(VariantCode.of("ABC-001"), List.of(), NOW);
        product.pullEvents();
        return product;
    }

    /**
     * 公開期間を持つ、バリアントが 1 つの非公開の商品
     */
    private Product productWithPeriod(PublicationPeriod period) {
        var product = productWithVariant();
        product.changePublicationPeriod(period, NOW);
        return product;
    }
}
