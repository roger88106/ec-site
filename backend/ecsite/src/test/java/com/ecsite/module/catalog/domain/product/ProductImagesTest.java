package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.model.id.AssetId;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.vo.ProductImageId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 画像の一覧の単体テスト
 *
 * <p>表示順の並び、ID で探す操作、次の表示順、追加・削除・並べ替え、画像の数の上限を確認する。
 */
class ProductImagesTest {

    // ============================== 定数 ==============================

    /**
     * 1 商品あたりの画像の上限（枚）
     */
    private static final int MAX_IMAGES = 20;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("作成")
    class Construction {

        @Test
        @DisplayName("画像は、表示順に並ぶ")
        void sortsByDisplayOrder() {
            // 準備：表示順が逆に並んだ画像
            var second = image(2);
            var first = image(1);

            // 操作：一覧を作る
            var images = new ProductImages(List.of(second, first));

            // 確認：表示順に並ぶ
            assertThat(images.list()).extracting(ProductImage::id).containsExactly(first.id(), second.id());
        }

        @Test
        @DisplayName("画像は上限の 20 枚まで持てる")
        void acceptsUpToLimit() {
            // 操作と確認：20 枚の一覧を作れる
            assertThatCode(() -> new ProductImages(images(MAX_IMAGES))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("画像が 21 枚あれば、値を含まないメッセージの IllegalArgumentException になる")
        void rejectsOverLimit() {
            // 操作と確認：21 枚の一覧は作れない
            assertThatThrownBy(() -> new ProductImages(images(MAX_IMAGES + 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining(String.valueOf(MAX_IMAGES + 1));
        }

        @Test
        @DisplayName("作った後に元の一覧を変えても、画像の一覧は変わらない")
        void isIndependentFromSource() {
            // 準備：1 枚の画像を持つ変更できる一覧
            var source = new ArrayList<>(List.of(image(1)));
            var images = new ProductImages(source);

            // 操作：元の一覧に画像を追加する
            source.add(image(2));

            // 確認：画像の一覧は 1 枚のまま
            assertThat(images.size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("ID で探す")
    class Require {

        @Test
        @DisplayName("ID が一致する画像を返す")
        void findsImage() {
            // 準備：2 枚の画像
            var target = image(2);
            var images = new ProductImages(List.of(image(1), target));

            // 操作と確認：ID で探すと、その画像が返る
            assertThat(images.require(target.id())).isSameAs(target);
        }

        @Test
        @DisplayName("一覧にない ID は、業務のエラー NOT_FOUND になる")
        void rejectsUnknownId() {
            // 準備：1 枚の画像
            var images = new ProductImages(List.of(image(1)));

            // 操作と確認：一覧にない ID は拒否される
            assertBusinessError(() -> images.require(new ProductImageId(UUID.randomUUID())), CommonErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("ID を含むかを返す")
        void tellsContainment() {
            // 準備：1 枚の画像
            var image = image(1);
            var images = new ProductImages(List.of(image));

            // 操作と確認：ある ID は含み、ない ID は含まない
            assertThat(images.contains(image.id())).isTrue();
            assertThat(images.contains(new ProductImageId(UUID.randomUUID()))).isFalse();
        }
    }

    @Nested
    @DisplayName("次の表示順")
    class NextDisplayOrder {

        @Test
        @DisplayName("画像がなければ 1")
        void isOneWhenEmpty() {
            // 操作と確認：空の一覧の次は 1
            assertThat(new ProductImages(List.of()).nextDisplayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("画像があれば、最大の表示順の次")
        void isMaxPlusOne() {
            // 準備：表示順が 1 と 5 の画像
            var images = new ProductImages(List.of(image(1), image(5)));

            // 操作と確認：5 の次の 6
            assertThat(images.nextDisplayOrder()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("追加")
    class Add {

        @Test
        @DisplayName("画像を末尾に加えた一覧を返す")
        void appendsImage() {
            // 準備：1 枚の画像
            var first = image(1);
            var images = new ProductImages(List.of(first));
            var added = image(2);

            // 操作：加える
            var result = images.add(added);

            // 確認：末尾に加わり、元の一覧は変わらない
            assertThat(result.list()).extracting(ProductImage::id).containsExactly(first.id(), added.id());
            assertThat(images.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("上限を超える画像を加えると、IllegalArgumentException になる")
        void rejectsOverLimit() {
            // 準備：20 枚の画像
            var images = new ProductImages(images(MAX_IMAGES));

            // 操作と確認：21 枚目は加えられない
            assertThatThrownBy(() -> images.add(image(MAX_IMAGES + 1))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("画像を加えて、ちょうど上限の 20 枚になる")
        void acceptsAddingUpToLimit() {
            // 準備：19 枚の画像
            var images = new ProductImages(images(MAX_IMAGES - 1));

            // 操作：20 枚目を加える
            var result = images.add(image(MAX_IMAGES));

            // 確認：20 枚になる
            assertThat(result.size()).isEqualTo(MAX_IMAGES);
        }
    }

    @Nested
    @DisplayName("削除")
    class Remove {

        @Test
        @DisplayName("ID が一致する画像を除いた一覧を返す")
        void removesTarget() {
            // 準備：2 枚の画像
            var first = image(1);
            var second = image(2);
            var images = new ProductImages(List.of(first, second));

            // 操作：1 枚目を削除する
            var result = images.remove(first.id());

            // 確認：2 枚目だけが残り、元の一覧は変わらない
            assertThat(result.list()).extracting(ProductImage::id).containsExactly(second.id());
            assertThat(images.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("一覧にない ID は、業務のエラー NOT_FOUND になる")
        void rejectsUnknownId() {
            // 準備：1 枚の画像
            var images = new ProductImages(List.of(image(1)));

            // 操作と確認：一覧にない ID は拒否される
            assertBusinessError(() -> images.remove(new ProductImageId(UUID.randomUUID())), CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("並べ替え")
    class Reorder {

        @Test
        @DisplayName("指定した順に、1 から表示順を付けた一覧を返す")
        void reordersAndRenumbers() {
            // 準備：表示順が 3 と 7 の画像
            var first = image(3);
            var second = image(7);
            var images = new ProductImages(List.of(first, second));

            // 操作：逆の順に並べ替える
            var result = images.reorder(List.of(second.id(), first.id()));

            // 確認：指定した順に並び、表示順は 1、2
            assertThat(result.list()).extracting(ProductImage::id).containsExactly(second.id(), first.id());
            assertThat(result.list()).extracting(ProductImage::displayOrder).containsExactly(1, 2);
        }

        @Test
        @DisplayName("画像と過不足なく一致しなければ、業務のエラー INVALID_IMAGE_ORDER になる")
        void rejectsMismatch() {
            // 準備：2 枚の画像
            var first = image(1);
            var images = new ProductImages(List.of(first, image(2)));

            // 操作と確認：1 枚だけを送ると拒否される
            assertBusinessError(() -> images.reorder(List.of(first.id())), ProductErrorCode.INVALID_IMAGE_ORDER);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 画像を作る。
     *
     * @param displayOrder 表示順
     * @return 画像
     */
    private static ProductImage image(int displayOrder) {
        return new ProductImage(new ProductImageId(UUID.randomUUID()), new AssetId(UUID.randomUUID()), displayOrder);
    }

    /**
     * 表示順が 1 から続く画像を作る。
     *
     * @param count 枚数
     * @return 画像
     */
    private static List<ProductImage> images(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(ProductImagesTest::image).toList();
    }
}
