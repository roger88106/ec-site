package com.ecsite.module.catalog.domain.category;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * カテゴリの単体テスト（T202）。
 */
class CategoryTest {

    private final CategoryFactory factory = new CategoryFactory();

    @Nested
    @DisplayName("登録")
    class Create {

        @Test
        @DisplayName("AC-8 親がなければ最上位（深さ 1）になる")
        void createsRoot() {
            var root = factory.create(null, "衣類", 1);

            assertThat(root.id()).isNotNull();
            assertThat(root.parentId()).isNull();
            assertThat(root.name()).isEqualTo("衣類");
            assertThat(root.depth()).isEqualTo(1);
            assertThat(root.displayOrder()).isEqualTo(1);
            assertThat(root.version()).isZero();
        }

        @Test
        @DisplayName("AC-8 親を指定すると、親の 1 つ下の階層になる")
        void createsChild() {
            var root = factory.create(null, "衣類", 1);

            var child = factory.create(root, "トップス", 2);

            assertThat(child.parentId()).isEqualTo(root.id());
            assertThat(child.depth()).isEqualTo(2);
            assertThat(child.displayOrder()).isEqualTo(2);
        }

        @Test
        @DisplayName("AC-9 3 階層目までは登録でき、4 階層目は拒否される")
        void rejectsFourthLevel() {
            var first = factory.create(null, "衣類", 1);
            var second = factory.create(first, "トップス", 1);
            var third = factory.create(second, "T シャツ", 1);

            assertThat(third.depth()).isEqualTo(3);
            assertBusinessError(() -> factory.create(third, "半袖", 1), CategoryErrorCode.CATEGORY_DEPTH_EXCEEDED);
        }

        @Test
        @DisplayName("名称は 50 文字まで")
        void acceptsNameUpTo50Characters() {
            assertThatCode(() -> factory.create(null, "あ".repeat(50), 1)).doesNotThrowAnyException();
            assertBusinessError(() -> factory.create(null, "あ".repeat(51), 1), CategoryErrorCode.INVALID_CATEGORY_NAME);
        }

        @Test
        @DisplayName("名称が空白なら拒否される")
        void rejectsBlankName() {
            assertBusinessError(() -> factory.create(null, " ", 1), CategoryErrorCode.INVALID_CATEGORY_NAME);
            assertBusinessError(() -> factory.create(null, null, 1), CategoryErrorCode.INVALID_CATEGORY_NAME);
        }
    }

    @Nested
    @DisplayName("名称の変更")
    class Rename {

        @Test
        @DisplayName("AC-11 名称を変更できる")
        void renames() {
            var category = factory.create(null, "衣類", 1);

            category.rename("ファッション");
        }

        @Test
        @DisplayName("変更後の名称も 50 文字まで。拒否されたら名称は変わらない")
        void rejectsInvalidName() {
            var category = factory.create(null, "衣類", 1);

            assertThatCode(() -> category.rename("あ".repeat(50))).doesNotThrowAnyException();
            assertBusinessError(() -> category.rename("あ".repeat(51)), CategoryErrorCode.INVALID_CATEGORY_NAME);
            assertBusinessError(() -> category.rename(""), CategoryErrorCode.INVALID_CATEGORY_NAME);
            assertThat(category.name()).isEqualTo("あ".repeat(50));
        }
    }

    @Nested
    @DisplayName("並び順の変更")
    class Reorder {

        @Test
        @DisplayName("AC-11 送った順に 1 から表示順を振り直し、表示順が変わったカテゴリを返す")
        void reorders() {
            var a = factory.create(null, "A", 1);
            var b = factory.create(null, "B", 2);
            var c = factory.create(null, "C", 3);

            var changed = Category.reorder(List.of(a, b, c), List.of(a.id(), c.id(), b.id()));

            assertThat(a.displayOrder()).isEqualTo(1);
            assertThat(c.displayOrder()).isEqualTo(2);
            assertThat(b.displayOrder()).isEqualTo(3);
            assertThat(changed).containsExactlyInAnyOrder(b, c);
        }

        @Test
        @DisplayName("兄弟が足りなければ拒否され、表示順は変わらない")
        void rejectsMissingSibling() {
            var a = factory.create(null, "A", 1);
            var b = factory.create(null, "B", 2);

            assertBusinessError(() -> Category.reorder(List.of(a, b), List.of(b.id())),
                    CategoryErrorCode.INVALID_CATEGORY_ORDER);
            assertThat(a.displayOrder()).isEqualTo(1);
            assertThat(b.displayOrder()).isEqualTo(2);
        }

        @Test
        @DisplayName("兄弟でないカテゴリが入っていれば拒否される")
        void rejectsUnknownCategory() {
            var a = factory.create(null, "A", 1);

            assertBusinessError(() -> Category.reorder(List.of(a), List.of(a.id(), UUID.randomUUID())),
                    CategoryErrorCode.INVALID_CATEGORY_ORDER);
        }

        @Test
        @DisplayName("同じカテゴリが重複していれば拒否される")
        void rejectsDuplicatedCategory() {
            var a = factory.create(null, "A", 1);
            var b = factory.create(null, "B", 2);

            assertBusinessError(() -> Category.reorder(List.of(a, b), List.of(a.id(), a.id(), b.id())),
                    CategoryErrorCode.INVALID_CATEGORY_ORDER);
        }
    }

    @Nested
    @DisplayName("復元")
    class Restore {

        @Test
        @DisplayName("スナップショットから復元できる")
        void restores() {
            var root = factory.create(null, "衣類", 1);
            var child = factory.create(root, "トップス", 2);

            var restored = factory.restore(child.toSnapshot());

            assertThat(restored.toSnapshot()).isEqualTo(child.toSnapshot());
        }
    }
}
