package com.ecsite.module.catalog.presentation;

import com.ecsite.common.model.id.CategoryId;
import com.ecsite.module.catalog.domain.category.port.CategoryRepository;
import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.CATEGORY;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 並び替えの途中で親カテゴリが削除されたときのテスト
 *
 * <p>並び替えは、親があることを確認してから兄弟をロックする。その間に親が削除された状態を、カテゴリのリポジトリの親の取得を差し替えて作る。
 * 親を取得した直後に、同じトランザクションで親の行を削除する（2 つのリクエストを同時に送る方法では、削除が確認とロックの間に起きるように調整できない）。
 *
 * <p>名称は、他のテストのカテゴリと重ならないよう実行ごとに作る。
 */
@IntegrationTest
@Transactional
class CategoryReorderParentDeletedTest {

    // ============================== 定数 ==============================

    /**
     * カテゴリの API のパス
     */
    private static final String BASE = "/api/admin/categories";

    /**
     * 送るカテゴリの表示順
     *
     * <p>別の親に 1 つ目の子として登録するので 1。
     */
    private static final int SENT_DISPLAY_ORDER = 1;

    /**
     * 送るカテゴリのバージョン
     *
     * <p>登録の後に変えないので 0。
     */
    private static final long SENT_VERSION = 0;

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    DSLContext dsl;

    @MockitoSpyBean
    CategoryRepository categoryRepository;

    // ============================== テスト ==============================

    @Test
    @DisplayName("親の確認の後に親が削除されると、並び替えは 400 になり、送ったカテゴリの並び順は変わらない")
    void rejectsWhenParentDeletedAfterCheck() throws Exception {
        // 準備：子のない親と、別の親の下の子（並び替えで送る ID）
        var catalog = new CatalogTestData(mockMvc, clock);
        var parent = catalog.category(uniqueName("衣類"), null);
        var otherParent = catalog.category(uniqueName("食品"), null);
        var sent = catalog.category(uniqueName("果物"), otherParent);
        deleteAfterFound(parent);

        // 操作：親の下の並び替えとして、別の親の子を送る
        var result = reorder(parent, sent);

        // 確認（前提）：親の判定の後、兄弟のロックの前に、親は削除された
        var order = inOrder(categoryRepository);
        order.verify(categoryRepository).findById(new CategoryId(parent));
        order.verify(categoryRepository).lockChildren(new CategoryId(parent));
        mockMvc.perform(get(BASE + "/" + parent)).andExpect(status().isNotFound());

        // 確認：兄弟と一致しない 400
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CATEGORY_ORDER"));

        // 確認：送ったカテゴリの表示順とバージョンは変わらない
        mockMvc.perform(get(BASE + "/" + sent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayOrder").value(SENT_DISPLAY_ORDER))
                .andExpect(jsonPath("$.version").value(SENT_VERSION));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 親カテゴリを取得した直後に、その行を削除するようにする。
     *
     * <p>削除する前に取得した親を返すので、並び替えは親があるとみなして兄弟のロックに進む。
     *
     * @param parentId 親カテゴリ ID
     */
    private void deleteAfterFound(UUID parentId) {
        doAnswer(invocation -> {
            var found = invocation.callRealMethod();
            dsl.deleteFrom(CATEGORY).where(CATEGORY.ID.eq(parentId)).execute();
            return found;
        }).when(categoryRepository).findById(new CategoryId(parentId));
    }

    /**
     * 他のテストのカテゴリと重ならない、テストごとの名称にする。
     *
     * @param name 名称
     * @return テストごとの名称
     */
    private static String uniqueName(String name) {
        return name + " " + UUID.randomUUID();
    }

    /**
     * 兄弟カテゴリの並び順を変える。
     *
     * @param parentId    親カテゴリ ID
     * @param categoryId  送るカテゴリ ID（1 つ）
     * @return 応答
     */
    private ResultActions reorder(UUID parentId, UUID categoryId) throws Exception {
        return mockMvc.perform(put(BASE + "/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"parentId": "%s", "categoryIds": ["%s"]}""".formatted(parentId, categoryId)));
    }
}
