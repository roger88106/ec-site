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
