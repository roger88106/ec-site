package com.ecsite.module.catalog.domain.category;

import com.ecsite.module.catalog.domain.CategoryErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * カテゴリの単体テスト
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る（準備した物から期待値を取り出さない）。
 */
class CategoryTest {

    // ============================== 定数 ==============================

    /**
     * 最上位のカテゴリの名称
     */
    private static final String ROOT_NAME = "衣類";

    /**
     * 2 階層目のカテゴリの名称
     */
    private static final String CHILD_NAME = "トップス";

    /**
     * 変更後の名称
     */
    private static final String NEW_NAME = "ファッション";

    /**
     * 名称の上限（文字）
     */
    private static final int NAME_MAX_LENGTH = 50;

    /**
     * カテゴリの生成
     */
    private final CategoryFactory factory = new CategoryFactory();

    // ============================== テスト ==============================

    @Nested
    @DisplayName("登録")
    class Create {

        @Test
        @DisplayName("AC-8 親がなければ最上位（深さ 1）になる")
        void createsRoot() {
            // 操作：親を指定せずに登録する
            var root = factory.create(null, ROOT_NAME, 1);

            // 確認：親がなく、深さが 1
            assertThat(root.parentId()).isNull();
            assertThat(root.depth()).isEqualTo(1);
        }

        @Test
        @DisplayName("AC-8 指定した名称と表示順で登録される")
        void createsWithNameAndDisplayOrder() {
            // 操作：名称と表示順を指定して登録する
            var root = factory.create(null, ROOT_NAME, 1);

            // 確認：指定した名称と表示順を持つ
            assertThat(root.name()).isEqualTo(ROOT_NAME);
            assertThat(root.displayOrder()).isEqualTo(1);
        }

        @Test
        @DisplayName("登録すると ID が振られる")
        void assignsId() {
            // 操作：登録する
            var root = factory.create(null, ROOT_NAME, 1);

            // 確認：ID がある
            assertThat(root.id()).isNotNull();
        }

        @Test
        @DisplayName("登録直後のバージョンは 0")
        void startsAtVersionZero() {
            // 操作：登録する
            var root = factory.create(null, ROOT_NAME, 1);

            // 確認：バージョンが 0
            assertThat(root.version()).isZero();
        }

        @Test
        @DisplayName("AC-8 親を指定すると、親の 1 つ下の階層になる")
        void createsChild() {
            // 準備：最上位のカテゴリ
            var root = factory.create(null, ROOT_NAME, 1);

            // 操作：その子を登録する
            var child = factory.create(root, CHILD_NAME, 2);

            // 確認：親がそのカテゴリで、深さが 2
            assertThat(child.parentId()).isEqualTo(root.id());
            assertThat(child.depth()).isEqualTo(2);
        }

        @Test
        @DisplayName("AC-8 子も、指定した表示順で登録される")
        void createsChildWithDisplayOrder() {
            // 準備：最上位のカテゴリ
            var root = factory.create(null, ROOT_NAME, 1);

            // 操作：表示順 2 で子を登録する
            var child = factory.create(root, CHILD_NAME, 2);

            // 確認：表示順が 2
            assertThat(child.displayOrder()).isEqualTo(2);
        }

        @Test
        @DisplayName("AC-9 3 階層目までは登録できる")
        void acceptsThirdLevel() {
            // 準備：2 階層目のカテゴリ
            var second = factory.create(factory.create(null, ROOT_NAME, 1), CHILD_NAME, 1);

            // 操作：その子を登録する
            var third = factory.create(second, "T シャツ", 1);

            // 確認：深さが 3
            assertThat(third.depth()).isEqualTo(3);
        }

        @Test
        @DisplayName("AC-9 4 階層目は拒否される")
        void rejectsFourthLevel() {
            // 準備：3 階層目のカテゴリ
            var third = factory.create(factory.create(factory.create(null, ROOT_NAME, 1), CHILD_NAME, 1), "T シャツ", 1);

            // 操作と確認：その子は、業務のエラー CATEGORY_DEPTH_EXCEEDED で拒否される
            assertBusinessError(() -> factory.create(third, "半袖", 1), CategoryErrorCode.CATEGORY_DEPTH_EXCEEDED);
        }

        @Test
        @DisplayName("名称は 50 文字まで指定できる")
        void acceptsNameUpTo50Characters() {
            // 操作と確認：50 文字の名称で登録できる
            assertThatCode(() -> factory.create(null, "あ".repeat(NAME_MAX_LENGTH), 1)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("名称が 51 文字以上なら拒否される")
        void rejectsNameOver50Characters() {
            // 操作と確認：業務のエラー INVALID_CATEGORY_NAME で拒否される
            assertBusinessError(() -> factory.create(null, "あ".repeat(NAME_MAX_LENGTH + 1), 1), CategoryErrorCode.INVALID_CATEGORY_NAME);
        }

        @ParameterizedTest(name = "「{0}」")
        @NullSource
        @ValueSource(strings = " ")
        @DisplayName("名称に空白以外の文字がなければ拒否される")
        void rejectsBlankName(String name) {
            // 操作と確認：業務のエラー INVALID_CATEGORY_NAME で拒否される
            assertBusinessError(() -> factory.create(null, name, 1), CategoryErrorCode.INVALID_CATEGORY_NAME);
        }
    }

    @Nested
    @DisplayName("子を持てるか")
    class CanHaveChildren {

        @Test
        @DisplayName("AC-9 2 階層目のカテゴリは子を持てる")
        void secondLevelCanHaveChildren() {
            // 操作と確認：深さ 2 は子を持てる
            assertThat(Category.canHaveChildren(2)).isTrue();
        }

        @Test
        @DisplayName("AC-9 3 階層目のカテゴリは子を持てない")
        void thirdLevelCannotHaveChildren() {
            // 操作と確認：深さ 3 は子を持てない
            assertThat(Category.canHaveChildren(3)).isFalse();
        }
    }

    @Nested
    @DisplayName("名称の変更")
    class Rename {

        @Test
        @DisplayName("AC-11 名称を変更できる")
        void renames() {
            // 準備：登録したカテゴリ
            var category = factory.create(null, ROOT_NAME, 1);

            // 操作：名称を変える
            category.rename(NEW_NAME);

            // 確認：変えた名称になる
            assertThat(category.name()).isEqualTo(NEW_NAME);
        }

        @Test
        @DisplayName("変更後の名称も 50 文字まで指定できる")
        void acceptsNameUpTo50Characters() {
            // 準備：登録したカテゴリ
            var category = factory.create(null, ROOT_NAME, 1);

            // 操作と確認：50 文字の名称に変えられる
            assertThatCode(() -> category.rename("あ".repeat(NAME_MAX_LENGTH))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("変更後の名称が 51 文字以上なら拒否される")
        void rejectsNameOver50Characters() {
            // 準備：登録したカテゴリ
            var category = factory.create(null, ROOT_NAME, 1);

            // 操作と確認：業務のエラー INVALID_CATEGORY_NAME で拒否される
            assertBusinessError(() -> category.rename("あ".repeat(NAME_MAX_LENGTH + 1)), CategoryErrorCode.INVALID_CATEGORY_NAME);
        }

        @Test
        @DisplayName("変更後の名称が空なら拒否される")
        void rejectsEmptyName() {
            // 準備：登録したカテゴリ
            var category = factory.create(null, ROOT_NAME, 1);

            // 操作と確認：業務のエラー INVALID_CATEGORY_NAME で拒否される
            assertBusinessError(() -> category.rename(""), CategoryErrorCode.INVALID_CATEGORY_NAME);
        }

        @Test
        @DisplayName("拒否されたら、名称は変わらない")
        void keepsNameWhenRejected() {
            // 準備：登録したカテゴリ
            var category = factory.create(null, ROOT_NAME, 1);

            // 確認（前提）：51 文字の名称と、空の名称が拒否される
            assertBusinessError(() -> category.rename("あ".repeat(NAME_MAX_LENGTH + 1)), CategoryErrorCode.INVALID_CATEGORY_NAME);
            assertBusinessError(() -> category.rename(""), CategoryErrorCode.INVALID_CATEGORY_NAME);

            // 確認：元の名称のまま
            assertThat(category.name()).isEqualTo(ROOT_NAME);
        }
    }

    @Nested
    @DisplayName("並び順の変更")
    class Reorder {

        @Test
        @DisplayName("AC-11 送った順に 1 から表示順を振り直す")
        void reorders() {
            // 準備：A、B、C の順の兄弟
            var a = factory.create(null, "A", 1);
            var b = factory.create(null, "B", 2);
            var c = factory.create(null, "C", 3);

            // 操作：A、C、B の順に並べ替える
            Category.reorder(List.of(a, b, c), List.of(a.id(), c.id(), b.id()));

            // 確認：A が 1、C が 2、B が 3
            assertThat(a.displayOrder()).isEqualTo(1);
            assertThat(c.displayOrder()).isEqualTo(2);
            assertThat(b.displayOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("AC-11 表示順が変わったカテゴリだけを返す")
        void returnsChangedCategories() {
            // 準備：A、B、C の順の兄弟
            var a = factory.create(null, "A", 1);
            var b = factory.create(null, "B", 2);
            var c = factory.create(null, "C", 3);

            // 操作：A、C、B の順に並べ替える
            var changed = Category.reorder(List.of(a, b, c), List.of(a.id(), c.id(), b.id()));

            // 確認：位置の変わった B と C だけ
            assertThat(changed).containsExactlyInAnyOrder(b, c);
        }

        @Test
        @DisplayName("兄弟が足りなければ拒否される")
        void rejectsMissingSibling() {
            // 準備：A、B の兄弟
            var a = factory.create(null, "A", 1);
            var b = factory.create(null, "B", 2);

            // 操作と確認：B だけは、業務のエラー INVALID_CATEGORY_ORDER で拒否される
            assertBusinessError(() -> Category.reorder(List.of(a, b), List.of(b.id())), CategoryErrorCode.INVALID_CATEGORY_ORDER);
        }

        @Test
        @DisplayName("拒否されたら、表示順は変わらない")
        void keepsDisplayOrdersWhenRejected() {
            // 準備：A、B の兄弟
            var a = factory.create(null, "A", 1);
            var b = factory.create(null, "B", 2);

            // 確認（前提）：B だけの並べ替えが拒否される
            assertBusinessError(() -> Category.reorder(List.of(a, b), List.of(b.id())), CategoryErrorCode.INVALID_CATEGORY_ORDER);

            // 確認：A が 1、B が 2 のまま
            assertThat(a.displayOrder()).isEqualTo(1);
            assertThat(b.displayOrder()).isEqualTo(2);
        }

        @Test
        @DisplayName("兄弟でないカテゴリが入っていれば拒否される")
        void rejectsUnknownCategory() {
            // 準備：A だけの兄弟
            var a = factory.create(null, "A", 1);

            // 操作と確認：知らない ID を混ぜると、業務のエラー INVALID_CATEGORY_ORDER で拒否される
            assertBusinessError(() -> Category.reorder(List.of(a), List.of(a.id(), UUID.randomUUID())),
                    CategoryErrorCode.INVALID_CATEGORY_ORDER);
        }

        @Test
        @DisplayName("同じカテゴリが重複していれば拒否される")
        void rejectsDuplicatedCategory() {
            // 準備：A、B の兄弟
            var a = factory.create(null, "A", 1);
            var b = factory.create(null, "B", 2);

            // 操作と確認：A を 2 回送ると、業務のエラー INVALID_CATEGORY_ORDER で拒否される
            assertBusinessError(() -> Category.reorder(List.of(a, b), List.of(a.id(), a.id(), b.id())),
                    CategoryErrorCode.INVALID_CATEGORY_ORDER);
        }
    }

    @Nested
    @DisplayName("復元")
    class Restore {

        @Test
        @DisplayName("スナップショットから、同じ内容のカテゴリに復元できる")
        void restores() {
            // 準備：2 階層目のカテゴリ
            var child = factory.create(factory.create(null, ROOT_NAME, 1), CHILD_NAME, 2);

            // 操作：スナップショットから復元する
            var restored = factory.restore(child.toSnapshot());

            // 確認：スナップショットが元のカテゴリと同じ
            assertThat(restored.toSnapshot()).isEqualTo(child.toSnapshot());
        }
    }
}
