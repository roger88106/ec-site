package com.ecsite.module.catalog.domain.product;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.catalog.domain.ProductErrorCode;
import com.ecsite.module.catalog.domain.product.vo.VariantCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ecsite.support.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * バリアントの一覧の単体テスト
 *
 * <p>表示順の並び、ID で探す操作、次の表示順、置き換え・追加・削除・並べ替え、画像の指定を外す操作を確認する。
 */
class VariantsTest {

    // ============================== テスト ==============================

    @Nested
    @DisplayName("作成")
    class Construction {

        @Test
        @DisplayName("バリアントは、表示順に並ぶ")
        void sortsByDisplayOrder() {
            // 準備：表示順が逆に並んだバリアント
            var second = variant(2);
            var first = variant(1);

            // 操作：一覧を作る
            var variants = new Variants(List.of(second, first));

            // 確認：表示順に並ぶ
            assertThat(variants.list()).extracting(Variant::id).containsExactly(first.id(), second.id());
        }

        @Test
        @DisplayName("作った後に元の一覧を変えても、バリアントの一覧は変わらない")
        void isIndependentFromSource() {
            // 準備：1 つのバリアントを持つ変更できる一覧
            var source = new ArrayList<>(List.of(variant(1)));
            var variants = new Variants(source);

            // 操作：元の一覧にバリアントを追加する
            source.add(variant(2));

            // 確認：バリアントの一覧は 1 つのまま
            assertThat(variants.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("空の一覧は、空であると答える")
        void tellsEmpty() {
            // 操作と確認：空の一覧は空で、1 つあれば空でない
            assertThat(new Variants(List.of()).isEmpty()).isTrue();
            assertThat(new Variants(List.of(variant(1))).isEmpty()).isFalse();
        }
    }

    @Nested
    @DisplayName("ID で探す")
    class Require {

        @Test
        @DisplayName("ID が一致するバリアントを返す")
        void findsVariant() {
            // 準備：2 つのバリアント
            var target = variant(2);
            var variants = new Variants(List.of(variant(1), target));

            // 操作と確認：ID で探すと、そのバリアントが返る
            assertThat(variants.require(target.id())).isSameAs(target);
        }

        @Test
        @DisplayName("ない ID は、業務のエラー NOT_FOUND になる")
        void rejectsUnknownId() {
            // 準備：1 つのバリアント
            var variants = new Variants(List.of(variant(1)));

            // 操作と確認：知らない ID は拒否される
            assertBusinessError(() -> variants.require(UUID.randomUUID()), CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("次の表示順")
    class NextDisplayOrder {

        @Test
        @DisplayName("バリアントがなければ 1")
        void isOneWhenEmpty() {
            // 操作と確認：空の一覧の次は 1
            assertThat(new Variants(List.of()).nextDisplayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("バリアントがあれば、最大の表示順の次")
        void isMaxPlusOne() {
            // 準備：表示順が 1 と 5 のバリアント
            var variants = new Variants(List.of(variant(1), variant(5)));

            // 操作と確認：5 の次の 6
            assertThat(variants.nextDisplayOrder()).isEqualTo(6);
        }
    }

    @Nested
    @DisplayName("置き換え")
    class Replace {

        @Test
        @DisplayName("ID が一致するバリアントだけを置き換えた一覧を返す")
        void replacesOnlyTarget() {
            // 準備：2 つのバリアント
            var first = variant(1);
            var second = variant(2);
            var variants = new Variants(List.of(first, second));

            // 操作：2 つ目を販売停止にする
            var replaced = variants.replace(second.id(), variant -> variant.withSalesSuspended(true));

            // 確認：2 つ目だけが替わり、元の一覧は変わらない
            assertThat(replaced.list()).extracting(Variant::salesSuspended).containsExactly(false, true);
            assertThat(replaced.list()).extracting(Variant::id).containsExactly(first.id(), second.id());
            assertThat(variants.require(second.id()).salesSuspended()).isFalse();
        }

        @Test
        @DisplayName("ない ID は、業務のエラー NOT_FOUND になる")
        void rejectsUnknownId() {
            // 準備：1 つのバリアント
            var variants = new Variants(List.of(variant(1)));

            // 操作と確認：知らない ID は拒否される
            assertBusinessError(() -> variants.replace(UUID.randomUUID(), variant -> variant), CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("画像の指定を外す")
    class WithoutImage {

        @Test
        @DisplayName("その画像を指定しているバリアントだけ、指定を外す")
        void clearsOnlyMatchingVariants() {
            // 準備：画像 A を指定した 2 つと、画像 B を指定した 1 つ
            var imageA = UUID.randomUUID();
            var imageB = UUID.randomUUID();
            var variants = new Variants(List.of(variant(1, imageA), variant(2, imageB), variant(3, imageA)));

            // 操作：画像 A の指定を外す
            var result = variants.withoutImage(imageA);

            // 確認：画像 A だけが外れ、並びは変わらない
            assertThat(result.list()).extracting(Variant::imageId).containsExactly(null, imageB, null);
            assertThat(result.list()).extracting(Variant::displayOrder).containsExactly(1, 2, 3);
        }
    }

    @Nested
    @DisplayName("追加")
    class Add {

        @Test
        @DisplayName("バリアントを末尾に加えた一覧を返す")
        void appendsVariant() {
            // 準備：1 つのバリアント
            var first = variant(1);
            var variants = new Variants(List.of(first));
            var added = variant(2);

            // 操作：加える
            var result = variants.add(added);

            // 確認：末尾に加わり、元の一覧は変わらない
            assertThat(result.list()).extracting(Variant::id).containsExactly(first.id(), added.id());
            assertThat(variants.size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("削除")
    class Remove {

        @Test
        @DisplayName("ID が一致するバリアントを除いた一覧を返す")
        void removesTarget() {
            // 準備：2 つのバリアント
            var first = variant(1);
            var second = variant(2);
            var variants = new Variants(List.of(first, second));

            // 操作：1 つ目を削除する
            var result = variants.remove(first.id());

            // 確認：2 つ目だけが残り、元の一覧は変わらない
            assertThat(result.list()).extracting(Variant::id).containsExactly(second.id());
            assertThat(variants.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("ない ID は、業務のエラー NOT_FOUND になる")
        void rejectsUnknownId() {
            // 準備：1 つのバリアント
            var variants = new Variants(List.of(variant(1)));

            // 操作と確認：知らない ID は拒否される
            assertBusinessError(() -> variants.remove(UUID.randomUUID()), CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("並べ替え")
    class Reorder {

        @Test
        @DisplayName("送った順に、1 から表示順を振った一覧を返す")
        void reordersAndRenumbers() {
            // 準備：表示順が 3 と 7 のバリアント
            var first = variant(3);
            var second = variant(7);
            var variants = new Variants(List.of(first, second));

            // 操作：逆の順に並べ替える
            var result = variants.reorder(List.of(second.id(), first.id()));

            // 確認：送った順に並び、表示順は 1、2
            assertThat(result.list()).extracting(Variant::id).containsExactly(second.id(), first.id());
            assertThat(result.list()).extracting(Variant::displayOrder).containsExactly(1, 2);
        }

        @Test
        @DisplayName("バリアントと過不足なく一致しなければ、業務のエラー INVALID_VARIANT_ORDER になる")
        void rejectsMismatch() {
            // 準備：2 つのバリアント
            var first = variant(1);
            var variants = new Variants(List.of(first, variant(2)));

            // 操作と確認：1 つだけを送ると拒否される
            assertBusinessError(() -> variants.reorder(List.of(first.id())), ProductErrorCode.INVALID_VARIANT_ORDER);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 軸の値がなく、画像の指定のないバリアントを作る。
     *
     * @param displayOrder 表示順
     * @return バリアント
     */
    private static Variant variant(int displayOrder) {
        return variant(displayOrder, null);
    }

    /**
     * 軸の値がなく、画像を指定したバリアントを作る。
     *
     * @param displayOrder 表示順
     * @param imageId      指定した画像の ID。なければ null
     * @return バリアント
     */
    private static Variant variant(int displayOrder, UUID imageId) {
        return new Variant(UUID.randomUUID(), VariantCode.of("ABC-001"), List.of(), displayOrder, false, imageId);
    }
}
