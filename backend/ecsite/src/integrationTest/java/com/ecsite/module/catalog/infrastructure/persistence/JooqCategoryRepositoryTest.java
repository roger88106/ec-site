package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.IntegrationTest;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.catalog.domain.CategoryErrorCode;
import com.ecsite.module.catalog.domain.category.Category;
import com.ecsite.module.catalog.domain.category.CategoryFactory;
import com.ecsite.module.catalog.domain.category.port.CategoryRepository;
import com.ecsite.module.catalog.domain.product.ProductFactory;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.ecsite.module.catalog.domain.product.vo.PriceExclTax;
import com.ecsite.module.catalog.domain.product.vo.ProductCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * カテゴリのリポジトリの統合テスト
 *
 * <p>削除と、子カテゴリの登録・商品への割り当てが同時に起きた場合を、アプリケーション層の確認を通さずにリポジトリを呼んで再現する。
 * テストごとにロールバックする。
 *
 * <p>DB には他のテストの最上位カテゴリもあるので、最上位に関わる確認は自分のカテゴリに絞る。
 */
@IntegrationTest
@Transactional
class JooqCategoryRepositoryTest {

    // ============================== 定数 ==============================

    /**
     * 商品の税区分 ID
     *
     * <p>税モジュールの ID なので、存在しなくてよい。
     */
    private static final UUID TAX_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * カテゴリの名称の上限（文字）
     */
    private static final int NAME_MAX_LENGTH = 50;

    // ============================== Bean ==============================

    @Autowired
    CategoryRepository repository;

    @Autowired
    CategoryFactory factory;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ProductFactory productFactory;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("削除と同時の変更")
    class ConcurrentChanges {

        @Test
        @DisplayName("AC-12 子カテゴリを持つカテゴリを削除すると、外部キーの違反が業務のエラーになる（500 にならない）")
        void rejectsDeletingParentByForeignKey() {
            // 準備：子カテゴリを持つカテゴリ
            var parent = added(null, "衣類 " + UUID.randomUUID());
            added(parent, "トップス");

            // 操作と確認：業務のエラー CATEGORY_HAS_CHILDREN で拒否される
            assertBusinessError(() -> repository.delete(parent), CategoryErrorCode.CATEGORY_HAS_CHILDREN);
        }

        @Test
        @DisplayName("AC-12 商品が所属しているカテゴリを削除すると、外部キーの違反が業務のエラーになる（500 にならない）")
        void rejectsDeletingCategoryWithProductsByForeignKey() {
            // 準備：商品が所属しているカテゴリ。削除の確認の後に、商品への割り当てが同時に起きた場合を再現する
            var category = added(null, "衣類 " + UUID.randomUUID());
            var product = productFactory.create(ProductCode.of("ABC-001"), "T シャツ", "",
                    TAX_CATEGORY_ID, PriceExclTax.of(1000), Set.of(category.id()), List.of());
            productRepository.add(product);

            // 操作と確認：業務のエラー CATEGORY_HAS_PRODUCTS で拒否される
            assertBusinessError(() -> repository.delete(category), CategoryErrorCode.CATEGORY_HAS_PRODUCTS);
        }

        @Test
        @DisplayName("親が削除されていれば、子カテゴリの登録は外部キーの違反で見つからないエラーになる（500 にならない）")
        void rejectsAddingChildOfDeletedParent() {
            // 準備：削除したカテゴリ
            var parent = added(null, "衣類 " + UUID.randomUUID());
            repository.delete(parent);

            // 操作と確認：その子の登録は、業務のエラー NOT_FOUND で拒否される
            assertBusinessError(() -> repository.add(factory.create(parent, "トップス", 1)), CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("取得した後の他の変更")
    class StaleCategory {

        @Test
        @DisplayName("取得した後に他で名称を変えられたカテゴリは、保存すると競合のエラーになる")
        void rejectsSavingStaleCategory() {
            // 準備：同じカテゴリを 2 回取得し、先の 1 つで名称を変えて保存する
            var category = added(null, "衣類 " + UUID.randomUUID());
            var first = repository.findById(category.id()).orElseThrow();
            var stale = repository.findById(category.id()).orElseThrow();
            first.rename("ファッション " + UUID.randomUUID());
            repository.save(first);

            // 操作と確認：古い方の保存は、業務のエラー CONCURRENT_MODIFICATION で拒否される
            stale.rename("服飾 " + UUID.randomUUID());
            assertBusinessError(() -> repository.save(stale), CommonErrorCode.CONCURRENT_MODIFICATION);
        }

        @Test
        @DisplayName("一括の保存で 1 つでも取得した後に他で変えられていれば、競合のエラーになる")
        void rejectsSavingAllWithStaleCategory() {
            // 準備：子の「トップス」「ボトムス」を取得した後、「トップス」だけを他で名称を変えて保存する
            var parent = added(null, "衣類 " + UUID.randomUUID());
            added(parent, "トップス");
            added(parent, "ボトムス");
            var siblings = repository.lockChildren(parent.id());
            var other = repository.findById(siblings.getFirst().id()).orElseThrow();
            other.rename("シャツ");
            repository.save(other);

            // 操作と確認：古い「トップス」を含む一括の保存は、業務のエラー CONCURRENT_MODIFICATION で拒否される
            siblings.forEach(sibling -> sibling.rename(sibling.name() + "（変更）"));
            assertBusinessError(() -> repository.saveAll(siblings), CommonErrorCode.CONCURRENT_MODIFICATION);
        }

        @Test
        @DisplayName("既に削除されたカテゴリの削除は、見つからないエラーになる")
        void rejectsDeletingDeletedCategory() {
            // 準備：削除したカテゴリ
            var category = added(null, "衣類 " + UUID.randomUUID());
            repository.delete(category);

            // 操作と確認：もう一度の削除は、業務のエラー NOT_FOUND で拒否される
            assertBusinessError(() -> repository.delete(category), CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("上限の値の保存")
    class MaxValues {

        @Test
        @DisplayName("名称の上限の 50 文字（サロゲートペア）を保存して、同じ値で読み戻せる")
        void savesMaxLengthName() {
            // 準備：サロゲートペアの文字だけの、50 文字の名称のカテゴリ
            var name = "𩸽".repeat(NAME_MAX_LENGTH);
            var category = factory.create(null, name, repository.nextDisplayOrder(null));

            // 操作：登録して読み戻す
            repository.add(category);
            var restored = repository.findById(category.id()).orElseThrow();

            // 確認：名称が同じ
            assertThat(restored.name()).isEqualTo(name);
        }
    }

    @Nested
    @DisplayName("兄弟の取得と表示順")
    class Siblings {

        @Test
        @DisplayName("最上位を指定すると、最上位のカテゴリだけを取得する")
        void locksRootChildren() {
            // 準備：最上位の「衣類」「食品」と、「衣類」の子の「トップス」
            var clothing = added(null, "衣類 " + UUID.randomUUID());
            var tops = added(clothing, "トップス");
            var food = added(null, "食品 " + UUID.randomUUID());

            // 操作：最上位の兄弟を取得する
            var roots = repository.lockChildren(null);

            // 確認：自分のカテゴリのうち、最上位の 2 つだけが含まれる
            assertThat(roots).extracting(Category::id).contains(clothing.id(), food.id()).doesNotContain(tops.id());
        }

        /**
         * 次の表示順が、兄弟の表示順の最大 ＋ 1 になることを確認する。
         *
         * <p>最上位の期待値は、準備で最後に登録した「食品」の表示順から作る。
         * DB には他のテストの最上位カテゴリが残り、最上位の表示順の最大は実行ごとに変わるため、定数にできない。
         */
        @Test
        @DisplayName("次の表示順は、兄弟の表示順の最大 ＋ 1")
        void nextDisplayOrderFollowsLastSibling() {
            // 準備：最上位の「衣類」「食品」と、「衣類」の子の「トップス」
            var clothing = added(null, "衣類 " + UUID.randomUUID());
            added(clothing, "トップス");
            var food = added(null, "食品 " + UUID.randomUUID());

            // 操作と確認：「衣類」の下は 2。最上位は、最後に登録した「食品」の次
            assertThat(repository.nextDisplayOrder(clothing.id())).isEqualTo(2);
            assertThat(repository.nextDisplayOrder(null)).isEqualTo(food.displayOrder() + 1);
        }

        @Test
        @DisplayName("子のないカテゴリの下の、次の表示順は 1")
        void nextDisplayOrderStartsAtOne() {
            // 準備：子のない最上位のカテゴリ
            var food = added(null, "食品 " + UUID.randomUUID());

            // 操作と確認：1 から始まる
            assertThat(repository.nextDisplayOrder(food.id())).isEqualTo(1);
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * カテゴリを作って、兄弟の最後に登録する。
     *
     * @param parent 親（最上位なら null）
     * @param name   名称
     * @return 登録したカテゴリ
     */
    private Category added(Category parent, String name) {
        var category = factory.create(parent, name, repository.nextDisplayOrder(parent == null ? null : parent.id()));
        repository.add(category);
        return category;
    }
}
