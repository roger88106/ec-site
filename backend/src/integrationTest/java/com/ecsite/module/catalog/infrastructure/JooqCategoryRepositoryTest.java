package com.ecsite.module.catalog.infrastructure;

import com.ecsite.IntegrationTest;
import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.module.catalog.domain.category.Category;
import com.ecsite.module.catalog.domain.category.CategoryErrorCode;
import com.ecsite.module.catalog.domain.category.CategoryFactory;
import com.ecsite.module.catalog.domain.category.port.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * カテゴリのリポジトリの統合テスト（T205）。<br />
 * 削除と子カテゴリの登録が同時に起きた場合を、アプリケーション層の確認を通さずにリポジトリを呼んで再現する。
 * テストごとにロールバックする。
 */
@IntegrationTest
@Transactional
class JooqCategoryRepositoryTest {

    @Autowired
    CategoryRepository repository;

    @Autowired
    CategoryFactory factory;

    @Test
    @DisplayName("AC-12 子カテゴリを持つカテゴリを削除すると、外部キーの違反が業務のエラーになる（500 にならない）")
    void rejectsDeletingParentByForeignKey() {
        var parent = added(null, "衣類");
        added(parent, "トップス");

        assertBusinessError(() -> repository.delete(parent), CategoryErrorCode.CATEGORY_HAS_CHILDREN);
    }

    @Test
    @DisplayName("親が削除されていれば、子カテゴリの登録は外部キーの違反で見つからないエラーになる（500 にならない）")
    void rejectsAddingChildOfDeletedParent() {
        var parent = added(null, "衣類");
        repository.delete(parent);

        assertBusinessError(() -> repository.add(factory.create(parent, "トップス", 1)), CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("最上位を指定すると、最上位のカテゴリだけを取得する")
    void locksRootChildren() {
        var parent = added(null, "衣類");
        added(parent, "トップス");
        var food = added(null, "食品");

        assertThat(repository.lockChildren(null)).extracting(Category::id).containsExactlyInAnyOrder(parent.id(), food.id());
        assertThat(repository.nextDisplayOrder(null)).isEqualTo(3);
        assertThat(repository.nextDisplayOrder(parent.id())).isEqualTo(2);
        assertThat(repository.nextDisplayOrder(food.id())).isEqualTo(1);
    }

    // ============================== 補助 ==============================

    private Category added(Category parent, String name) {
        var category = factory.create(parent, name, repository.nextDisplayOrder(parent == null ? null : parent.id()));
        repository.add(category);
        return category;
    }
}
