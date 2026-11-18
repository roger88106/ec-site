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
 * <p>表示順の並びと、ID で探す操作・次の表示順・重複の問い合わせ・置き換え・追加・削除・並べ替え・画像の指定を外す操作を確認する。
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
        @DisplayName("空の一覧は、空と判定される")
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
        @DisplayName("一覧にない ID は、業務のエラー NOT_FOUND になる")
        void rejectsUnknownId() {
            // 準備：1 つのバリアント
            var variants = new Variants(List.of(variant(1)));

            // 操作と確認：一覧にない ID は拒否される
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
    @DisplayName("重複の問い合わせ")
    class Duplication {

        @Test
        @DisplayName("同じバリアント品番のバリアントがあれば、品番を含む")
        void containsSameCode() {
            // 準備：品番 V1 のバリアントを持つ一覧
            var variants = new Variants(List.of(variant(1, VariantCode.of("V1"), List.of())));

            // 操作と確認：V1 を含む
            assertThat(variants.containsCode(VariantCode.of("V1"))).isTrue();
        }

        @Test
        @DisplayName("同じバリアント品番のバリアントがなければ、品番を含まない")
        void doesNotContainOtherCode() {
            // 準備：品番 V1 のバリアントを持つ一覧
            var variants = new Variants(List.of(variant(1, VariantCode.of("V1"), List.of())));

            // 操作と確認：V2 は含まない
            assertThat(variants.containsCode(VariantCode.of("V2"))).isFalse();
        }

        @Test
        @DisplayName("同じ軸の値の組み合わせのバリアントがあれば、軸キーを含む")
        void containsSameAxisKey() {
            // 準備：白、M のバリアントを持つ一覧
            var variants = new Variants(List.of(variant(1, VariantCode.of("V1"), List.of("白", "M"))));

            // 操作と確認：白、M の軸キーを含む
            assertThat(variants.containsAxisKey(variant(2, VariantCode.of("V2"), List.of("白", "M")).axisKey())).isTrue();
        }

        @Test
        @DisplayName("軸の値の組み合わせが違えば、軸キーを含まない")
        void doesNotContainOtherAxisKey() {
            // 準備：白、M のバリアントを持つ一覧
            var variants = new Variants(List.of(variant(1, VariantCode.of("V1"), List.of("白", "M"))));

            // 操作と確認：白、L の軸キーは含まない
            assertThat(variants.containsAxisKey(variant(2, VariantCode.of("V2"), List.of("白", "L")).axisKey())).isFalse();
        }

        @Test
        @DisplayName("軸の値の数が指定した数と違うバリアントがあれば、true を返す")
        void detectsOtherAxisValueCount() {
            // 準備：軸の値が 2 つのバリアントを持つ一覧
            var variants = new Variants(List.of(variant(1, VariantCode.of("V1"), List.of("白", "M"))));

            // 操作と確認：軸が 1 つなら、数が違う
            assertThat(variants.hasAxisValueCountOtherThan(1)).isTrue();
        }

        @Test
        @DisplayName("全てのバリアントで軸の値の数が指定した数と同じなら、false を返す")
        void acceptsSameAxisValueCount() {
            // 準備：軸の値が 2 つのバリアントを持つ一覧
            var variants = new Variants(List.of(variant(1, VariantCode.of("V1"), List.of("白", "M"))));

            // 操作と確認：軸が 2 つなら、数は同じ
            assertThat(variants.hasAxisValueCountOtherThan(2)).isFalse();
        }

        @Test
        @DisplayName("バリアントがなければ、軸の値の数が違うバリアントはない")
        void hasNoOtherAxisValueCountWhenEmpty() {
            // 準備：空の一覧
            var variants = new Variants(List.of());

            // 操作と確認：どの数でも false
            assertThat(variants.hasAxisValueCountOtherThan(1)).isFalse();
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
        @DisplayName("一覧にない ID は、業務のエラー NOT_FOUND になる")
        void rejectsUnknownId() {
            // 準備：1 つのバリアント
            var variants = new Variants(List.of(variant(1)));

            // 操作と確認：一覧にない ID は拒否される
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
        @DisplayName("一覧にない ID は、業務のエラー NOT_FOUND になる")
        void rejectsUnknownId() {
            // 準備：1 つのバリアント
            var variants = new Variants(List.of(variant(1)));

            // 操作と確認：一覧にない ID は拒否される
            assertBusinessError(() -> variants.remove(UUID.randomUUID()), CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("並べ替え")
    class Reorder {

        @Test
        @DisplayName("指定した順に、1 から表示順を付けた一覧を返す")
        void reordersAndRenumbers() {
            // 準備：表示順が 3 と 7 のバリアント
            var first = variant(3);
            var second = variant(7);
            var variants = new Variants(List.of(first, second));

            // 操作：逆の順に並べ替える
            var result = variants.reorder(List.of(second.id(), first.id()));

            // 確認：指定した順に並び、表示順は 1、2
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

    /**
     * 品番と軸の値を指定して、画像の指定のないバリアントを作る。
     *
     * @param displayOrder 表示順
     * @param code         バリアント品番
     * @param axisValues   軸の値
     * @return バリアント
     */
    private static Variant variant(int displayOrder, VariantCode code, List<String> axisValues) {
        return new Variant(UUID.randomUUID(), code, axisValues, displayOrder, false, null);
    }
}
