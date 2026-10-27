package com.ecsite.module.catalog.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * カテゴリ API の受け入れテスト。<br />
 * テストごとにロールバックする。統合テストでは seed を入れないので、カテゴリはテストで作ったものだけになる。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class CategoryApiTest {

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/categories";

    /**
     * 存在しない ID
     */
    private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;


    @Nested
    @DisplayName("AC-8、9、10 カテゴリの登録")
    class Create {

        @Test
        @DisplayName("AC-8 親を指定しなければ最上位になる。Location の URL で、そのカテゴリを取得できる")
        void createsRootAndGetsByLocation() throws Exception {
            var rootCount = roots().size();
            var name = uniqueName("衣類");
            // CPD-OFF 登録の API の応答（201、Location、ID）の確認そのものを読めるように、各 API のテストに書く。補助に移すと確認の内容が読めなくなる
            var result = createCategory(name, null)
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*" + BASE + "/[0-9a-f-]{36}")))
                    .andExpect(jsonPath("$.id").isString())
                    .andReturn();
            String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

            mockMvc.perform(get(result.getResponse().getHeader(HttpHeaders.LOCATION)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    // CPD-ON
                    .andExpect(jsonPath("$.parentId").value(nullValue()))
                    .andExpect(jsonPath("$.name").value(name))
                    .andExpect(jsonPath("$.depth").value(1))
                    .andExpect(jsonPath("$.displayOrder").value(rootCount + 1))
                    .andExpect(jsonPath("$.version").value(0));
        }

        @Test
        @DisplayName("AC-8 親を指定すると、その子になり、兄弟の最後に並ぶ")
        void createsChildAtLast() throws Exception {
            var parent = createdId("衣類", null);
            createdId("トップス", parent);

            var child = createdId("ボトムス", parent);

            mockMvc.perform(get(BASE + "/" + child))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentId").value(parent))
                    .andExpect(jsonPath("$.depth").value(2))
                    .andExpect(jsonPath("$.displayOrder").value(2));
        }

        @Test
        @DisplayName("AC-9 3 階層目までは登録でき、4 階層目は 400")
        void rejectsFourthLevel() throws Exception {
            var first = createdId("衣類", null);
            var second = createdId("トップス", first);
            var third = createdId("T シャツ", second);

            createCategory("半袖", third)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_DEPTH_EXCEEDED"));
        }

        @Test
        @DisplayName("AC-10 同じ親の下で名称が重複すれば 409")
        void rejectsDuplicatedNameUnderSameParent() throws Exception {
            var parent = createdId("衣類", null);
            createdId("トップス", parent);

            createCategory("トップス", parent)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_NAME_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("AC-10 最上位同士で名称が重複しても 409")
        void rejectsDuplicatedRootName() throws Exception {
            createdId("衣類", null);

            createCategory("衣類", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_NAME_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("AC-10 親が違えば同じ名称で登録できる")
        void acceptsSameNameUnderDifferentParent() throws Exception {
            var men = createdId("メンズ", null);
            var women = createdId("レディース", null);
            createdId("トップス", men);

            createCategory("トップス", women).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("存在しない親を指定すれば 404")
        void rejectsUnknownParent() throws Exception {
            createCategory("トップス", UNKNOWN_ID)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("名称が空なら 400")
        void rejectsEmptyName() throws Exception {
            createCategory("", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("名称が空白だけなら 400")
        void rejectsBlankName() throws Exception {
            createCategory(" ", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CATEGORY_NAME"));
        }
    }

    @Nested
    @DisplayName("取得")
    class Get {

        @Test
        @DisplayName("存在しない ID は 404")
        void returnsNotFound() throws Exception {
            mockMvc.perform(get(BASE + "/" + UNKNOWN_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("木は、各階層が並び順どおりに返る")
        void listsTreeInDisplayOrder() throws Exception {
            var others = rootIds();
            var root = uniqueName("衣類");
            var clothing = createdId(root, null);
            var food = createdId(uniqueName("食品"), null);
            var tops = createdId("トップス", clothing);
            var bottoms = createdId("ボトムス", clothing);
            createdId("T シャツ", tops);
            createdId("シャツ", tops);
            reorder(null, withOthers(others, food, clothing)).andExpect(status().isNoContent());
            reorder(clothing, bottoms, tops).andExpect(status().isNoContent());

            // 他のテストの最上位のカテゴリが間に入っても、自分の 2 つの相対の順序は並び順どおりになる
            assertThat(rootsOf(clothing, food)).extracting(r -> r.get("id")).containsExactly(food, clothing);
            var node = nodeOf(root);
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(node + ".depth", contains(1)))
                    .andExpect(jsonPath(node + ".children[*].name", contains("ボトムス", "トップス")))
                    .andExpect(jsonPath(node + ".children[0].children").value(contains(empty())))
                    .andExpect(jsonPath(node + ".children[1].depth", contains(2)))
                    .andExpect(jsonPath(node + ".children[1].children[*].name", contains("T シャツ", "シャツ")))
                    .andExpect(jsonPath(node + ".children[1].children[0].depth", contains(3)));
        }

        @Test
        @DisplayName("AC-270 木の節は子のカテゴリを足せるかを持ち、いちばん深いカテゴリだけ足せない")
        void listsAddChildAction() throws Exception {
            var root = uniqueName("衣類");
            var clothing = createdId(root, null);
            var tops = createdId("トップス", clothing);
            createdId("T シャツ", tops);

            var node = nodeOf(root);
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(node + ".actions.addChild", contains(true)))
                    .andExpect(jsonPath(node + ".children[0].actions.addChild", contains(true)))
                    .andExpect(jsonPath(node + ".children[0].children[0].actions.addChild", contains(false)));
        }
    }

    @Nested
    @DisplayName("AC-11 名称の変更")
    class Rename {

        @Test
        @DisplayName("AC-11 名称を変更でき、バージョンが上がる")
        void renames() throws Exception {
            var id = createdId("衣類", null);

            rename(id, "ファッション", 0).andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/" + id))
                    .andExpect(jsonPath("$.name").value("ファッション"))
                    .andExpect(jsonPath("$.version").value(1));
        }

        @Test
        @DisplayName("AC-10 兄弟と同じ名称への変更は 409")
        void rejectsDuplicatedName() throws Exception {
            createdId("衣類", null);
            var food = createdId("食品", null);

            rename(food, "衣類", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_NAME_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("取得時のバージョンと一致しなければ 409")
        void rejectsVersionMismatch() throws Exception {
            var id = createdId("衣類", null);
            rename(id, "ファッション", 0).andExpect(status().isNoContent());

            rename(id, "アパレル", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("存在しないカテゴリは 404")
        void rejectsUnknownCategory() throws Exception {
            rename(UNKNOWN_ID, "衣類", 0)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("AC-11 並び順の変更")
    class Reorder {

        @Test
        @DisplayName("AC-11 最上位のカテゴリを並べ替えられる。表示順が変わったカテゴリはバージョンが上がる")
        void reordersRoots() throws Exception {
            var others = rootIds();
            var a = createdId(uniqueName("A"), null);
            var b = createdId(uniqueName("B"), null);
            var c = createdId(uniqueName("C"), null);

            reorder(null, withOthers(others, a, c, b)).andExpect(status().isNoContent());

            var mine = rootsOf(a, c, b);
            assertThat(mine).extracting(r -> r.get("id")).containsExactly(a, c, b);
            assertThat(mine).extracting(r -> r.get("displayOrder"))
                    .containsExactly(others.size() + 1, others.size() + 2, others.size() + 3);
            assertThat(mine).extracting(r -> r.get("version")).containsExactly(0, 1, 1);
        }

        @Test
        @DisplayName("AC-11 子カテゴリを並べ替えられる")
        void reordersChildren() throws Exception {
            var root = uniqueName("衣類");
            var parent = createdId(root, null);
            var tops = createdId("トップス", parent);
            var bottoms = createdId("ボトムス", parent);

            reorder(parent, bottoms, tops).andExpect(status().isNoContent());

            mockMvc.perform(get(BASE))
                    .andExpect(jsonPath(nodeOf(root) + ".children[*].name", contains("ボトムス", "トップス")));
        }

        @Test
        @DisplayName("並べ替えの前に取得したバージョンで名称を変えると 409")
        void renameAfterReorderConflicts() throws Exception {
            var others = rootIds();
            var a = createdId(uniqueName("A"), null);
            var b = createdId(uniqueName("B"), null);
            reorder(null, withOthers(others, b, a)).andExpect(status().isNoContent());

            rename(a, "AA", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("兄弟が足りなければ 400。並びは変わらない")
        void rejectsMissingSibling() throws Exception {
            var others = rootIds();
            var a = createdId(uniqueName("A"), null);
            var b = createdId(uniqueName("B"), null);

            reorder(null, withOthers(others, a))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CATEGORY_ORDER"));
            assertThat(rootsOf(a, b)).extracting(r -> r.get("id")).containsExactly(a, b);
        }

        @Test
        @DisplayName("別の親のカテゴリが入っていれば 400")
        void rejectsCategoryOfAnotherParent() throws Exception {
            var parent = createdId("衣類", null);
            var tops = createdId("トップス", parent);

            reorder(null, parent, tops)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CATEGORY_ORDER"));
        }

        @Test
        @DisplayName("存在しない親を指定すれば 404")
        void rejectsUnknownParent() throws Exception {
            var a = createdId("A", null);

            reorder(UNKNOWN_ID, a)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("AC-12 カテゴリの削除")
    class Delete {

        @Test
        @DisplayName("子カテゴリを持たないカテゴリは削除できる")
        void deletesLeaf() throws Exception {
            var parent = createdId("衣類", null);
            var child = createdId("トップス", parent);

            mockMvc.perform(delete(BASE + "/" + child)).andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/" + child)).andExpect(status().isNotFound());
            mockMvc.perform(delete(BASE + "/" + parent)).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("AC-12 子カテゴリを持つカテゴリは削除できず 400")
        void rejectsCategoryWithChildren() throws Exception {
            var parent = createdId("衣類", null);
            createdId("トップス", parent);

            mockMvc.perform(delete(BASE + "/" + parent))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_HAS_CHILDREN"));
            mockMvc.perform(get(BASE + "/" + parent)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("存在しないカテゴリは 404")
        void rejectsUnknownCategory() throws Exception {
            mockMvc.perform(delete(BASE + "/" + UNKNOWN_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    // ============================== 補助 ==============================

    /**
     * 他のテストが確定させた最上位のカテゴリと重ならない、テストごとの名称にする
     */
    private static String uniqueName(String name) {
        return name + " " + UUID.randomUUID();
    }

    /**
     * 木の中から、名称で最上位のカテゴリを選ぶ JSONPath（他のテストの最上位のカテゴリが木に混ざっていても、自分の部分木だけを見る）
     */
    private static String nodeOf(String rootName) {
        return "$.items[?(@.name=='%s')]".formatted(rootName);
    }

    /**
     * 今の最上位のカテゴリを、表示順に読む（他のテストが確定させたものを含む）
     */
    private List<Map<String, Object>> roots() throws Exception {
        var body = mockMvc.perform(get(BASE)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items");
    }

    /**
     * 今の最上位のカテゴリの ID を、表示順に読む
     */
    private List<String> rootIds() throws Exception {
        return roots().stream().map(r -> (String) r.get("id")).toList();
    }

    /**
     * 今の最上位のカテゴリのうち、指定した ID のものを、表示順に返す
     */
    private List<Map<String, Object>> rootsOf(String... ids) throws Exception {
        var wanted = Set.of(ids);
        return roots().stream().filter(r -> wanted.contains((String) r.get("id"))).toList();
    }

    /**
     * 並べ替えの API は兄弟の全てを求めるので、今ある最上位のカテゴリの後ろに、自分のカテゴリを並べた一覧にする
     */
    private static String[] withOthers(List<String> others, String... mine) {
        return Stream.concat(others.stream(), Arrays.stream(mine)).toArray(String[]::new);
    }

    /**
     * カテゴリを登録する。親がなければ最上位
     */
    private ResultActions createCategory(String name, String parentId) throws Exception {
        var parent = parentId == null ? "null" : "\"%s\"".formatted(parentId);
        return mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "parentId": %s}""".formatted(name, parent)));
    }

    /**
     * カテゴリを登録して、その ID を返す
     */
    private String createdId(String name, String parentId) throws Exception {
        var parent = parentId == null ? null : UUID.fromString(parentId);
        return new CatalogTestData(mockMvc, clock).category(name, parent).toString();
    }

    /**
     * カテゴリの名称を変える
     */
    private ResultActions rename(String id, String name, long version) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "version": %d}""".formatted(name, version)));
    }

    /**
     * 兄弟のカテゴリを、指定した順に並び替える。親がなければ最上位
     */
    private ResultActions reorder(String parentId, String... categoryIds) throws Exception {
        var parent = parentId == null ? "null" : "\"%s\"".formatted(parentId);
        var ids = Arrays.stream(categoryIds).map("\"%s\""::formatted).collect(Collectors.joining(", "));
        return mockMvc.perform(put(BASE + "/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"parentId": %s, "categoryIds": [%s]}""".formatted(parent, ids)));
    }
}
