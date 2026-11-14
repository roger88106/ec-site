package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 商品（集約）の商品画像の単体テスト
 *
 * <p>画像の追加・並び順・バリアントの画像の指定・削除・復元を確認する。ファイルの受け付けと保存はメディアモジュールが行うので、
 * ここでは画像 ID とアセット ID だけを扱う。
 *
 * <p>画像 ID とアセット ID は実行ごとにテストの中で作り、それを期待値にも使う（準備した商品から取り出さない）。
 */
class ProductImageTest {

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
     * 1 商品あたりの画像の上限（枚）
     */
    private static final int MAX_IMAGES = 20;

    /**
     * 商品の生成
     */
    private final ProductFactory factory = new ProductFactory();

    // ============================== テスト ==============================

    @Nested
    @DisplayName("画像の追加")
    class AddImage {

        @Test
        @DisplayName("AC-37 画像は並びの最後に加わる")
        void appendsToEnd() {
            // 準備：画像のない商品
            var product = productWithVariant();
            var first = UUID.randomUUID();
            var second = UUID.randomUUID();

            // 操作：2 枚加える
            product.addImage(first, UUID.randomUUID());
            product.addImage(second, UUID.randomUUID());

            // 確認：加えた順に並ぶ
            assertThat(product.images()).extracting(ProductImage::id).containsExactly(first, second);
        }

        @Test
        @DisplayName("AC-37 加えた画像は、指定したアセットを持つ")
        void keepsAssetIds() {
            // 準備：画像のない商品
            var product = productWithVariant();
            var firstAsset = UUID.randomUUID();
            var secondAsset = UUID.randomUUID();

            // 操作：2 枚加える
            product.addImage(UUID.randomUUID(), firstAsset);
            product.addImage(UUID.randomUUID(), secondAsset);

            // 確認：並びの順にアセット ID を持つ
            assertThat(product.images()).extracting(ProductImage::assetId).containsExactly(firstAsset, secondAsset);
        }

        @Test
        @DisplayName("AC-37 先頭の画像が代表画像になる")
        void firstImageIsRepresentative() {
            // 準備：画像のない商品
            var product = productWithVariant();
            var first = UUID.randomUUID();

            // 操作：2 枚加える
            product.addImage(first, UUID.randomUUID());
            product.addImage(UUID.randomUUID(), UUID.randomUUID());

            // 確認：先頭が最初に加えた画像
            assertThat(product.images()).first().extracting(ProductImage::id).isEqualTo(first);
        }

        @Test
        @DisplayName("画像がなければ代表画像もない")
        void hasNoRepresentativeImageWithoutImages() {
            // 操作：画像のない商品を作る
            var product = productWithVariant();

            // 確認：画像がない
            assertThat(product.images()).isEmpty();
        }

        @Test
        @DisplayName("AC-40 画像は 1 商品あたり 20 枚まで加えられる")
        void acceptsUpToTwentyImages() {
            // 操作：20 枚加える
            var product = productWithImages(imageIds(MAX_IMAGES));

            // 確認：20 枚を持つ
            assertThat(product.images()).hasSize(MAX_IMAGES);
        }

        @Test
        @DisplayName("AC-40 21 枚目の画像は拒否される")
        void rejectsTwentyFirstImage() {
            // 準備：20 枚の画像を持つ商品
            var product = productWithImages(imageIds(MAX_IMAGES));

            // 操作と確認：業務のエラー TOO_MANY_PRODUCT_IMAGES で拒否される
            assertBusinessError(() -> product.addImage(UUID.randomUUID(), UUID.randomUUID()),
                    ProductErrorCode.TOO_MANY_PRODUCT_IMAGES);
        }

        @Test
        @DisplayName("AC-40 拒否された 21 枚目は加わらない")
        void keepsTwentyImagesWhenRejected() {
            // 準備：20 枚の画像を持つ商品
            var product = productWithImages(imageIds(MAX_IMAGES));

            // 確認（前提）：21 枚目が拒否される
            assertBusinessError(() -> product.addImage(UUID.randomUUID(), UUID.randomUUID()),
                    ProductErrorCode.TOO_MANY_PRODUCT_IMAGES);

            // 確認：20 枚のまま
            assertThat(product.images()).hasSize(MAX_IMAGES);
        }

        @Test
        @DisplayName("AC-40 上限に達していなければ、受付の前の枚数の確認を通る")
        void verifiesCapacityBelowLimit() {
            // 準備：19 枚の画像を持つ商品
            var product = productWithImages(imageIds(MAX_IMAGES - 1));

            // 操作と確認：枚数の確認を通る
            assertThatCode(product::verifyCanAddImage).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC-40 上限に達していれば、受付の前の枚数の確認で拒否される")
        void verifiesCapacityAtLimit() {
            // 準備：20 枚の画像を持つ商品
            var product = productWithImages(imageIds(MAX_IMAGES));

            // 操作と確認：業務のエラー TOO_MANY_PRODUCT_IMAGES で拒否される
            assertBusinessError(product::verifyCanAddImage, ProductErrorCode.TOO_MANY_PRODUCT_IMAGES);
        }
    }

    @Nested
    @DisplayName("画像の並び順")
    class ReorderImages {

        @Test
        @DisplayName("AC-37 送った順に並べ替えられる")
        void reordersImages() {
            // 準備：1、2、3 の順の画像を持つ商品
            var ids = imageIds(3);
            var product = productWithImages(ids);

            // 操作：3、1、2 の順に並べ替える
            product.reorderImages(List.of(ids.get(2), ids.getFirst(), ids.get(1)));

            // 確認：送った順に並ぶ
            assertThat(product.images()).extracting(ProductImage::id).containsExactly(ids.get(2), ids.getFirst(), ids.get(1));
        }

        @Test
        @DisplayName("AC-37 並べ替えた後は、先頭の画像が代表画像になる")
        void firstImageIsRepresentativeAfterReorder() {
            // 準備：1、2、3 の順の画像を持つ商品
            var ids = imageIds(3);
            var product = productWithImages(ids);

            // 操作：3、1、2 の順に並べ替える
            product.reorderImages(List.of(ids.get(2), ids.getFirst(), ids.get(1)));

            // 確認：先頭が 3
            assertThat(product.images()).first().extracting(ProductImage::id).isEqualTo(ids.get(2));
        }

        @Test
        @DisplayName("AC-37 送った画像が足りなければ拒否される")
        void rejectsMissingImage() {
            // 準備：3 枚の画像を持つ商品
            var ids = imageIds(3);
            var product = productWithImages(ids);

            // 操作と確認：2 枚だけは、業務のエラー INVALID_IMAGE_ORDER で拒否される
            assertBusinessError(() -> product.reorderImages(List.of(ids.getFirst(), ids.get(1))),
                    ProductErrorCode.INVALID_IMAGE_ORDER);
        }

        @Test
        @DisplayName("AC-37 同じ画像を重ねて送れば拒否される")
        void rejectsDuplicatedImage() {
            // 準備：3 枚の画像を持つ商品
            var ids = imageIds(3);
            var product = productWithImages(ids);

            // 操作と確認：1 を 2 回送ると、業務のエラー INVALID_IMAGE_ORDER で拒否される
            assertBusinessError(() -> product.reorderImages(List.of(ids.getFirst(), ids.getFirst(), ids.get(1))),
                    ProductErrorCode.INVALID_IMAGE_ORDER);
        }

        @Test
        @DisplayName("AC-37 商品にない画像を送れば拒否される")
        void rejectsUnknownImage() {
            // 準備：3 枚の画像を持つ商品
            var ids = imageIds(3);
            var product = productWithImages(ids);

            // 操作と確認：知らない ID を混ぜると、業務のエラー INVALID_IMAGE_ORDER で拒否される
            assertBusinessError(() -> product.reorderImages(List.of(ids.getFirst(), ids.get(1), UUID.randomUUID())),
                    ProductErrorCode.INVALID_IMAGE_ORDER);
        }
    }

    @Nested
    @DisplayName("バリアントの画像")
    class VariantImage {

        @Test
        @DisplayName("AC-38 バリアントに、その商品の画像を 1 枚指定できる")
        void setsVariantImage() {
            // 準備：2 枚の画像を持つ商品
            var ids = imageIds(2);
            var product = productWithImages(ids);
            var variantId = product.variants().getFirst().id();

            // 操作：2 枚目を指定する
            product.setVariantImage(variantId, ids.get(1));

            // 確認：バリアントの画像が 2 枚目
            assertThat(product.variants().getFirst().imageId()).isEqualTo(ids.get(1));
        }

        @Test
        @DisplayName("AC-38 バリアントの画像の指定を外せる")
        void clearsVariantImage() {
            // 準備：バリアントに画像を指定した商品
            var ids = imageIds(2);
            var product = productWithImages(ids);
            var variantId = product.variants().getFirst().id();
            product.setVariantImage(variantId, ids.get(1));

            // 操作：指定を外す
            product.setVariantImage(variantId, null);

            // 確認：バリアントの画像がない
            assertThat(product.variants().getFirst().imageId()).isNull();
        }

        @Test
        @DisplayName("AC-38 その商品にない画像は、バリアントに指定できない")
        void rejectsForeignImage() {
            // 準備：1 枚の画像を持つ商品
            var product = productWithImages(imageIds(1));
            var variantId = product.variants().getFirst().id();

            // 操作と確認：ほかの画像は、業務のエラー INVALID_VARIANT_IMAGE で拒否される
            assertBusinessError(() -> product.setVariantImage(variantId, UUID.randomUUID()),
                    ProductErrorCode.INVALID_VARIANT_IMAGE);
        }

        @Test
        @DisplayName("AC-38 商品にないバリアントには、画像を指定できない")
        void rejectsUnknownVariant() {
            // 準備：1 枚の画像を持つ商品
            var ids = imageIds(1);
            var product = productWithImages(ids);

            // 操作と確認：知らないバリアントは、業務のエラー NOT_FOUND で拒否される
            assertBusinessError(() -> product.setVariantImage(UUID.randomUUID(), ids.getFirst()), CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("画像の削除")
    class DeleteImage {

        @Test
        @DisplayName("画像を削除すると、並びから消える")
        void removesImage() {
            // 準備：2 枚の画像を持つ商品
            var ids = imageIds(2);
            var product = productWithImages(ids);

            // 操作：1 枚目を削除する
            product.deleteImage(ids.getFirst());

            // 確認：2 枚目だけが残る
            assertThat(product.images()).extracting(ProductImage::id).containsExactly(ids.get(1));
        }

        @Test
        @DisplayName("画像を削除すると、その画像を指定していたバリアントの指定が外れる")
        void clearsVariantImage() {
            // 準備：1 枚目をバリアントに指定した商品
            var ids = imageIds(2);
            var product = productWithImages(ids);
            product.setVariantImage(product.variants().getFirst().id(), ids.getFirst());

            // 操作：1 枚目を削除する
            product.deleteImage(ids.getFirst());

            // 確認：バリアントの画像がない
            assertThat(product.variants().getFirst().imageId()).isNull();
        }

        @Test
        @DisplayName("画像を削除すると、手放すアセットの ID を返す")
        void returnsReleasedAssetId() {
            // 準備：アセットを指定した画像を持つ商品
            var product = productWithVariant();
            var imageId = UUID.randomUUID();
            var assetId = UUID.randomUUID();
            product.addImage(imageId, assetId);

            // 操作：画像を削除する
            var released = product.deleteImage(imageId);

            // 確認：その画像のアセット ID
            assertThat(released).isEqualTo(assetId);
        }

        @Test
        @DisplayName("画像の削除では、イベントは登録されない")
        void registersNoEvent() {
            // 準備：2 枚の画像を持つ商品
            var ids = imageIds(2);
            var product = productWithImages(ids);

            // 操作：1 枚目を削除する
            product.deleteImage(ids.getFirst());

            // 確認：イベントがない
            assertThat(product.pullEvents()).isEmpty();
        }

        @Test
        @DisplayName("C 側に表示されたことのある商品の画像も削除できる")
        void deletesImageOfShownProduct() {
            // 準備：今日公開して表示された、1 枚の画像を持つ商品
            var ids = imageIds(1);
            var product = productWithImages(ids);
            product.publish(NOW);

            // 操作と確認：削除できる
            assertThatCode(() -> product.deleteImage(ids.getFirst())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("商品にない画像は削除できない")
        void rejectsUnknownImage() {
            // 準備：1 枚の画像を持つ商品
            var product = productWithImages(imageIds(1));

            // 操作と確認：業務のエラー NOT_FOUND で拒否される
            assertBusinessError(() -> product.deleteImage(UUID.randomUUID()), CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("復元")
    class Restore {

        @Test
        @DisplayName("画像とバリアントの画像の指定も、スナップショットから復元できる")
        void restoresImages() {
            // 準備：2 枚目をバリアントに指定した商品
            var ids = imageIds(2);
            var product = productWithImages(ids);
            product.setVariantImage(product.variants().getFirst().id(), ids.get(1));

            // 操作：スナップショットから復元する
            var restored = factory.restore(product.toSnapshot());

            // 確認：スナップショットが元の商品と同じ
            assertThat(restored.toSnapshot()).isEqualTo(product.toSnapshot());
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 画像 ID を作る。
     *
     * @param count 枚数
     * @return 画像 ID（実行ごとに作る）
     */
    private static List<UUID> imageIds(int count) {
        return Stream.generate(UUID::randomUUID).limit(count).toList();
    }

    /**
     * 軸がなく、バリアントを 1 つ持つ非公開の商品を作る（画像なし）。
     *
     * <p>バリアント登録のイベントは捨てておく。
     *
     * @return 非公開の商品
     */
    private Product productWithVariant() {
        var product = factory.create(ProductCode.of("ABC-001"), "T シャツ", "", TAX_CATEGORY_ID, PriceExclTax.of(1000),
                Set.of(), List.of());
        product.addVariant(VariantCode.of("ABC-001"), List.of(), NOW);
        product.pullEvents();
        return product;
    }

    /**
     * バリアントが 1 つで、指定した画像を並びの順に持つ非公開の商品を作る。
     *
     * <p>アセット ID は画像ごとに作る。
     *
     * @param imageIds 画像 ID（並びの順）
     * @return 非公開の商品
     */
    private Product productWithImages(List<UUID> imageIds) {
        var product = productWithVariant();
        imageIds.forEach(imageId -> product.addImage(imageId, UUID.randomUUID()));
        return product;
    }
}
