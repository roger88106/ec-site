package com.ecsite.module.catalog.presentation;

import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
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
 * カテゴリ API の受け入れテスト
 *
 * <p>テストごとにロールバックする。統合テストでは seed を入れないが、ロールバックしないほかのテストが確定させた最上位のカテゴリは残りうる。
 * そこで、最上位のカテゴリの名称はテストごとに変え、並びは自分のカテゴリの相対の順序だけを確認する。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る。
 */
@IntegrationTest
@Transactional
class CategoryApiTest {

    // ============================== 定数 ==============================

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/categories";

    /**
     * 存在しない ID
     */
    private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

    /**
     * カテゴリ「衣類」の名称
     */
    private static final String CLOTHING = "衣類";

    /**
     * カテゴリ「食品」の名称
     */
    private static final String FOOD = "食品";

    /**
     * カテゴリ「トップス」の名称
     */
    private static final String TOPS = "トップス";

    /**
     * カテゴリ「ボトムス」の名称
     */
    private static final String BOTTOMS = "ボトムス";

    /**
     * カテゴリ「T シャツ」の名称
     */
    private static final String T_SHIRT = "T シャツ";

    /**
     * カテゴリ「シャツ」の名称
     */
    private static final String SHIRT = "シャツ";

    /**
     * カテゴリ「半袖」の名称
     */
    private static final String SHORT_SLEEVE = "半袖";

    /**
     * カテゴリ「メンズ」の名称
     */
    private static final String MENS = "メンズ";

    /**
     * カテゴリ「レディース」の名称
     */
    private static final String WOMENS = "レディース";

    /**
     * 変更した後の名称
     */
    private static final String FASHION = "ファッション";

    /**
     * 名称の上限（文字）
     */
    private static final int NAME_MAX_LENGTH = 50;

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    /**
     * カテゴリを作る補助
     */
    private CatalogTestData catalog;

    @BeforeEach
    void setUp() {
        catalog = new CatalogTestData(mockMvc, clock);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("カテゴリの登録")
    class Create {

        @Test
        @DisplayName("AC-8 親を指定しなければ最上位になる")
        void createsRoot() throws Exception {
            // 操作：親を指定せずに登録する
            var id = createdId(uniqueName(CLOTHING), null);

            // 確認：親がなく、深さが 1
            mockMvc.perform(get(BASE + "/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentId").value(nullValue()))
                    .andExpect(jsonPath("$.depth").value(1));
        }

        @Test
        @DisplayName("登録した最上位のカテゴリは、最上位の最後に並ぶ")
        void appendsRootAtLast() throws Exception {
            // 準備：今ある最上位のカテゴリの数
            var rootCount = roots().size();

            // 操作：親を指定せずに登録する
            var id = createdId(uniqueName(CLOTHING), null);

            // 確認：表示順が、今ある最上位のカテゴリの次
            mockMvc.perform(get(BASE + "/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayOrder").value(rootCount + 1));
        }

        @Test
        @DisplayName("登録すると 201 と Location を返し、Location の URL でそのカテゴリを取得できる")
        void returnsLocation() throws Exception {
            // 準備：テストごとの名称
            var name = uniqueName(CLOTHING);

            // 操作と確認：201 と、カテゴリの URL の Location と、ID を返す
            // CPD-OFF 登録の API の応答（201、Location、ID）の確認そのものを読めるように、各 API のテストに書く。補助に移すと確認の内容が読めなくなる
            var result = createCategory(name, null)
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*" + BASE + "/[0-9a-f-]{36}")))
                    .andExpect(jsonPath("$.id").isString())
                    .andReturn();
            String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

            // 確認：Location の URL で、登録した名称の、バージョン 0 のカテゴリを取得できる
            mockMvc.perform(get(result.getResponse().getHeader(HttpHeaders.LOCATION)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    // CPD-ON
                    .andExpect(jsonPath("$.name").value(name))
                    .andExpect(jsonPath("$.version").value(0));
        }

        @Test
        @DisplayName("AC-8 親を指定すると、その子として 1 つ下の階層になる")
        void createsChild() throws Exception {
            // 準備：親のカテゴリ
            var parent = createdId(CLOTHING, null);

            // 操作：親を指定して登録する
            var child = createdId(TOPS, parent);

            // 確認：親が指定したカテゴリで、深さが 2
            mockMvc.perform(get(BASE + "/" + child))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parentId").value(parent))
                    .andExpect(jsonPath("$.depth").value(2));
        }

        @Test
        @DisplayName("登録した子カテゴリは、兄弟の最後に並ぶ")
        void appendsChildAtLast() throws Exception {
            // 準備：子を 1 つ持つ親のカテゴリ
            var parent = createdId(CLOTHING, null);
            createdId(TOPS, parent);

            // 操作：同じ親の下に、2 つ目の子を登録する
            var child = createdId(BOTTOMS, parent);

            // 確認：表示順が 2
            mockMvc.perform(get(BASE + "/" + child))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayOrder").value(2));
        }

        @Test
        @DisplayName("AC-9 3 階層目は登録できる")
        void acceptsThirdLevel() throws Exception {
            // 準備：2 階層のカテゴリ
            var first = createdId(CLOTHING, null);
            var second = createdId(TOPS, first);

            // 操作：2 階層目の下に登録する（201 は登録の補助が確認する）
            var third = createdId(T_SHIRT, second);

            // 確認：深さが 3
            mockMvc.perform(get(BASE + "/" + third))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.depth").value(3));
        }

        @Test
        @DisplayName("AC-9 4 階層目の登録は 400")
        void rejectsFourthLevel() throws Exception {
            // 準備：3 階層のカテゴリ
            var first = createdId(CLOTHING, null);
            var second = createdId(TOPS, first);
            var third = createdId(T_SHIRT, second);

            // 操作と確認：3 階層目の下への登録は、深さの上限の超過で 400
            createCategory(SHORT_SLEEVE, third)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_DEPTH_EXCEEDED"));
        }

        @Test
        @DisplayName("AC-10 同じ親の下で名称が重複すれば 409")
        void rejectsDuplicatedNameUnderSameParent() throws Exception {
            // 準備：子「トップス」を持つ親
            var parent = createdId(CLOTHING, null);
            createdId(TOPS, parent);

            // 操作と確認：同じ親の下に同じ名称を登録すると 409
            createCategory(TOPS, parent)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_NAME_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("AC-10 最上位同士で名称が重複しても 409")
        void rejectsDuplicatedRootName() throws Exception {
            // 準備：最上位のカテゴリ「衣類」
            createdId(CLOTHING, null);

            // 操作と確認：最上位に同じ名称を登録すると 409
            createCategory(CLOTHING, null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_NAME_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("AC-10 親が違えば同じ名称で登録できる")
        void acceptsSameNameUnderDifferentParent() throws Exception {
            // 準備：「メンズ」の下に「トップス」、別の最上位「レディース」
            var men = createdId(MENS, null);
            var women = createdId(WOMENS, null);
            createdId(TOPS, men);

            // 操作と確認：「レディース」の下に「トップス」を登録できる
            createCategory(TOPS, women).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("存在しない親を指定すれば 404")
        void rejectsUnknownParent() throws Exception {
            // 操作と確認：見つからないので 404
            createCategory(TOPS, UNKNOWN_ID)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("名称が空なら 400")
        void rejectsEmptyName() throws Exception {
            // 操作と確認：リクエストの検証で 400
            createCategory("", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("名称が空白だけなら 400")
        void rejectsBlankName() throws Exception {
            // 操作と確認：業務の検証で 400
            createCategory(" ", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CATEGORY_NAME"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1, NAME_MAX_LENGTH})
        @DisplayName("名称が範囲の端の文字数なら登録できる")
        void acceptsNameAtBoundary(int length) throws Exception {
            // 準備：親のカテゴリ「衣類」
            var parent = createdId(CLOTHING, null);

            // 操作と確認：登録できる
            createCategory("あ".repeat(length), parent).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("名称が上限を 1 文字超えれば 400")
        void rejectsNameOverMaximum() throws Exception {
            // 操作と確認：リクエストの検証で 400
            createCategory("あ".repeat(NAME_MAX_LENGTH + 1), null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
    }

    @Nested
    @DisplayName("取得")
    class Get {

        @Test
        @DisplayName("存在しない ID は 404")
        void returnsNotFound() throws Exception {
            // 操作と確認：見つからないので 404
            mockMvc.perform(get(BASE + "/" + UNKNOWN_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("木は、各階層を並び順どおりに返す")
        void listsTreeInDisplayOrder() throws Exception {
            // 準備：並べ替えの API で、各階層を並べ替えた木
            var tree = sortedTree();

            // 操作と確認：最上位は、他のテストの最上位のカテゴリが間に入っても、自分の 2 つの相対の順序が並び順どおり
            assertThat(rootsOf(tree.clothingId(), tree.foodId())).extracting(r -> r.get("id"))
                    .containsExactly(tree.foodId(), tree.clothingId());

            // 操作と確認：子と孫は並び順どおり
            var node = nodeOf(tree.rootName());
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(node + ".children[*].name", contains(BOTTOMS, TOPS)))
                    .andExpect(jsonPath(node + ".children[1].children[*].name", contains(T_SHIRT, SHIRT)));
        }

        @Test
        @DisplayName("木の節は、階層の深さを持つ")
        void listsDepth() throws Exception {
            // 準備：3 階層の木
            var node = nodeOf(sortedTree().rootName());

            // 操作と確認：最上位・子・孫の深さが 1・2・3
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(node + ".depth", contains(1)))
                    .andExpect(jsonPath(node + ".children[1].depth", contains(2)))
                    .andExpect(jsonPath(node + ".children[1].children[0].depth", contains(3)));
        }

        @Test
        @DisplayName("子のない節は、空の子の一覧を持つ")
        void listsEmptyChildren() throws Exception {
            // 準備：子のない「ボトムス」を含む木
            var node = nodeOf(sortedTree().rootName());

            // 操作と確認：「ボトムス」の子の一覧が空
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(node + ".children[0].children").value(contains(empty())));
        }

        @Test
        @DisplayName("AC-270 木の節は子のカテゴリを足せるかを持ち、いちばん深いカテゴリだけ足せない")
        void listsAddChildAction() throws Exception {
            // 準備：3 階層の木
            var root = uniqueName(CLOTHING);
            var clothing = createdId(root, null);
            var tops = createdId(TOPS, clothing);
            createdId(T_SHIRT, tops);

            // 操作と確認：1・2 階層目は足せて、3 階層目は足せない
            var node = nodeOf(root);
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(node + ".actions.addChild", contains(true)))
                    .andExpect(jsonPath(node + ".children[0].actions.addChild", contains(true)))
                    .andExpect(jsonPath(node + ".children[0].children[0].actions.addChild", contains(false)));
        }
    }

    @Nested
    @DisplayName("名称の変更")
    class Rename {

        @Test
        @DisplayName("AC-11 名称を変更できる")
        void renames() throws Exception {
            // 準備：カテゴリ「衣類」
            var id = createdId(CLOTHING, null);

            // 操作：「ファッション」に変える
            rename(id, FASHION, 0).andExpect(status().isNoContent());

            // 確認：名称が変わっている
            mockMvc.perform(get(BASE + "/" + id)).andExpect(jsonPath("$.name").value(FASHION));
        }

        @Test
        @DisplayName("AC-11 名称を変更すると、バージョンが上がる")
        void incrementsVersionOnRename() throws Exception {
            // 準備：バージョン 0 のカテゴリ
            var id = createdId(CLOTHING, null);

            // 操作：名称を変える
            rename(id, FASHION, 0).andExpect(status().isNoContent());

            // 確認：バージョンが 1
            mockMvc.perform(get(BASE + "/" + id)).andExpect(jsonPath("$.version").value(1));
        }

        @Test
        @DisplayName("AC-10 兄弟と同じ名称への変更は 409")
        void rejectsDuplicatedName() throws Exception {
            // 準備：最上位の「衣類」と「食品」
            createdId(CLOTHING, null);
            var food = createdId(FOOD, null);

            // 操作と確認：「食品」を「衣類」に変えると 409
            rename(food, CLOTHING, 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_NAME_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("取得時のバージョンと一致しなければ 409")
        void rejectsVersionMismatch() throws Exception {
            // 準備：名称を 1 回変えて、バージョンが 1 のカテゴリ
            var id = createdId(CLOTHING, null);
            rename(id, FASHION, 0).andExpect(status().isNoContent());

            // 操作と確認：古いバージョン 0 で変えると 409
            rename(id, "アパレル", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("存在しないカテゴリは 404")
        void rejectsUnknownCategory() throws Exception {
            // 操作と確認：見つからないので 404
            rename(UNKNOWN_ID, CLOTHING, 0)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1, NAME_MAX_LENGTH})
        @DisplayName("変更後の名称が範囲の端の文字数なら変更できる")
        void acceptsNameAtBoundary(int length) throws Exception {
            // 準備：カテゴリ「衣類」の子「トップス」
            var id = createdId(TOPS, createdId(CLOTHING, null));

            // 操作と確認：変更できる
            rename(id, "あ".repeat(length), 0).andExpect(status().isNoContent());
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, NAME_MAX_LENGTH + 1})
        @DisplayName("変更後の名称が範囲の外の文字数なら 400")
        void rejectsNameOutOfRange(int length) throws Exception {
            // 準備：カテゴリ「衣類」
            var id = createdId(CLOTHING, null);

            // 操作と確認：リクエストの検証で 400
            rename(id, "あ".repeat(length), 0)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
    }

    @Nested
    @DisplayName("並び順の変更")
    class Reorder {

        @Test
        @DisplayName("AC-11 最上位のカテゴリを、送った順に並べ替えられる")
        void reordersRoots() throws Exception {
            // 準備：今ある最上位のカテゴリの後ろに、A・B・C
            var others = rootIds();
            var abc = createAbc();

            // 操作：A・C・B の順に並べ替える
            reorder(null, withOthers(others, abc.a(), abc.c(), abc.b())).andExpect(status().isNoContent());

            // 確認：自分の 3 つが A・C・B の順
            assertThat(rootsOf(abc.a(), abc.b(), abc.c())).extracting(r -> r.get("id")).containsExactly(abc.a(), abc.c(), abc.b());
        }

        @Test
        @DisplayName("AC-11 並べ替えると、表示順が送った順の連番になる")
        void renumbersDisplayOrder() throws Exception {
            // 準備：今ある最上位のカテゴリの後ろに、A・B・C
            var others = rootIds();
            var abc = createAbc();

            // 操作：A・C・B の順に並べ替える
            reorder(null, withOthers(others, abc.a(), abc.c(), abc.b())).andExpect(status().isNoContent());

            // 確認：A・C・B の表示順が、今ある最上位のカテゴリに続く連番
            assertThat(rootsOf(abc.a(), abc.b(), abc.c())).extracting(r -> r.get("displayOrder"))
                    .containsExactly(others.size() + 1, others.size() + 2, others.size() + 3);
        }

        @Test
        @DisplayName("AC-11 表示順が変わったカテゴリだけ、バージョンが上がる")
        void incrementsVersionOfMoved() throws Exception {
            // 準備：今ある最上位のカテゴリの後ろに、A・B・C（全てバージョン 0）
            var others = rootIds();
            var abc = createAbc();

            // 操作：A・C・B の順に並べ替える（A は動かない）
            reorder(null, withOthers(others, abc.a(), abc.c(), abc.b())).andExpect(status().isNoContent());

            // 確認：A・C・B の順に、バージョンが 0・1・1
            assertThat(rootsOf(abc.a(), abc.b(), abc.c())).extracting(r -> r.get("version")).containsExactly(0, 1, 1);
        }

        @Test
        @DisplayName("AC-11 子カテゴリを並べ替えられる")
        void reordersChildren() throws Exception {
            // 準備：子「トップス」「ボトムス」を、この順に持つ親
            var root = uniqueName(CLOTHING);
            var parent = createdId(root, null);
            var tops = createdId(TOPS, parent);
            var bottoms = createdId(BOTTOMS, parent);

            // 操作：「ボトムス」「トップス」の順に並べ替える
            reorder(parent, bottoms, tops).andExpect(status().isNoContent());

            // 確認：木の子が「ボトムス」「トップス」の順
            mockMvc.perform(get(BASE))
                    .andExpect(jsonPath(nodeOf(root) + ".children[*].name", contains(BOTTOMS, TOPS)));
        }

        @Test
        @DisplayName("並べ替えの前に取得したバージョンで名称を変えると 409")
        void renameAfterReorderConflicts() throws Exception {
            // 準備：A・B を登録し、B・A の順に並べ替える（A のバージョンが 1 に上がる）
            var others = rootIds();
            var a = createdId(uniqueName("A"), null);
            var b = createdId(uniqueName("B"), null);
            reorder(null, withOthers(others, b, a)).andExpect(status().isNoContent());

            // 操作と確認：並べ替えの前のバージョン 0 で A の名称を変えると 409
            rename(a, "AA", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("兄弟が足りなければ 400")
        void rejectsMissingSibling() throws Exception {
            // 準備：最上位の A・B
            var others = rootIds();
            var a = createdId(uniqueName("A"), null);
            createdId(uniqueName("B"), null);

            // 操作と確認：B を抜いて送ると 400
            reorder(null, withOthers(others, a))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CATEGORY_ORDER"));
        }

        @Test
        @DisplayName("兄弟が 1 つだけなら、その 1 つの並びで並べ替えられる")
        void reordersSingleChild() throws Exception {
            // 準備：子が「トップス」だけの「衣類」
            var parent = createdId(CLOTHING, null);
            var tops = createdId(TOPS, parent);

            // 操作と確認：並べ替えられる
            reorder(parent, tops).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("空の並びは 400")
        void rejectsEmptyOrder() throws Exception {
            // 準備：子が「トップス」だけの「衣類」
            var parent = createdId(CLOTHING, null);
            createdId(TOPS, parent);

            // 操作と確認：リクエストの検証で 400
            reorder(parent)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("兄弟が足りずに拒否したとき、並びは変わらない")
        void keepsOrderWhenSiblingMissing() throws Exception {
            // 準備：最上位の A・B
            var others = rootIds();
            var a = createdId(uniqueName("A"), null);
            var b = createdId(uniqueName("B"), null);

            // 操作：B を抜いて送る
            reorder(null, withOthers(others, a)).andExpect(status().isBadRequest());

            // 確認：A・B の順のまま
            assertThat(rootsOf(a, b)).extracting(r -> r.get("id")).containsExactly(a, b);
        }

        @Test
        @DisplayName("別の親のカテゴリが入っていれば 400")
        void rejectsCategoryOfAnotherParent() throws Exception {
            // 準備：最上位の「衣類」と、その子「トップス」
            var parent = createdId(CLOTHING, null);
            var tops = createdId(TOPS, parent);

            // 操作と確認：最上位の並べ替えに子を混ぜると 400
            reorder(null, parent, tops)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CATEGORY_ORDER"));
        }

        @Test
        @DisplayName("存在しない親を指定すれば 404")
        void rejectsUnknownParent() throws Exception {
            // 準備：最上位のカテゴリ
            var a = createdId("A", null);

            // 操作と確認：存在しない親の下の並べ替えは 404
            reorder(UNKNOWN_ID, a)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("カテゴリの削除")
    class Delete {

        @Test
        @DisplayName("子カテゴリを持たないカテゴリは削除でき、取得できなくなる")
        void deletesLeaf() throws Exception {
            // 準備：子のない「トップス」
            var parent = createdId(CLOTHING, null);
            var child = createdId(TOPS, parent);

            // 操作：「トップス」を削除する
            mockMvc.perform(delete(BASE + "/" + child)).andExpect(status().isNoContent());

            // 確認：取得すると 404
            mockMvc.perform(get(BASE + "/" + child)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("子カテゴリを全て削除した親は、削除できる")
        void deletesParentAfterChildren() throws Exception {
            // 準備：子を削除した親
            var parent = createdId(CLOTHING, null);
            var child = createdId(TOPS, parent);
            mockMvc.perform(delete(BASE + "/" + child)).andExpect(status().isNoContent());

            // 操作と確認：親を削除できる
            mockMvc.perform(delete(BASE + "/" + parent)).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("AC-12 子カテゴリを持つカテゴリは削除できず 400")
        void rejectsCategoryWithChildren() throws Exception {
            // 準備：子を持つ親
            var parent = createdId(CLOTHING, null);
            createdId(TOPS, parent);

            // 操作と確認：親の削除は 400
            mockMvc.perform(delete(BASE + "/" + parent))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_HAS_CHILDREN"));
        }

        @Test
        @DisplayName("AC-12 子カテゴリを持つために削除を拒否したカテゴリは、残る")
        void keepsCategoryWithChildren() throws Exception {
            // 準備：子を持つ親
            var parent = createdId(CLOTHING, null);
            createdId(TOPS, parent);

            // 操作：親を削除する
            mockMvc.perform(delete(BASE + "/" + parent)).andExpect(status().isBadRequest());

            // 確認：親を取得できる
            mockMvc.perform(get(BASE + "/" + parent)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("存在しないカテゴリは 404")
        void rejectsUnknownCategory() throws Exception {
            // 操作と確認：見つからないので 404
            mockMvc.perform(delete(BASE + "/" + UNKNOWN_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 木の中から、名称で最上位のカテゴリを選ぶ JSONPath を返す。
     *
     * <p>他のテストの最上位のカテゴリが木に混ざっていても、自分の部分木だけを見る。
     *
     * @param rootName 最上位のカテゴリの名称
     * @return JSONPath
     */
    private static String nodeOf(String rootName) {
        return "$.items[?(@.name=='%s')]".formatted(rootName);
    }

    /**
     * 今の最上位のカテゴリを、表示順に読む（他のテストが確定させたものを含む）。
     *
     * @return 最上位のカテゴリ
     */
    private List<Map<String, Object>> roots() throws Exception {
        var body = mockMvc.perform(get(BASE)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items");
    }

    /**
     * 今の最上位のカテゴリの ID を、表示順に読む。
     *
     * @return 最上位のカテゴリの ID
     */
    private List<String> rootIds() throws Exception {
        return roots().stream().map(r -> (String) r.get("id")).toList();
    }

    /**
     * 今の最上位のカテゴリのうち、指定した ID のものを、表示順に返す。
     *
     * @param ids カテゴリ ID
     * @return 指定した ID の最上位のカテゴリ
     */
    private List<Map<String, Object>> rootsOf(String... ids) throws Exception {
        var wanted = Set.of(ids);
        return roots().stream().filter(r -> wanted.contains((String) r.get("id"))).toList();
    }

    // ============================== 準備の補助 ==============================

    /**
     * 他のテストが確定させた最上位のカテゴリと重ならない、テストごとの名称にする。
     *
     * @param name 名称
     * @return テストごとの名称
     */
    private static String uniqueName(String name) {
        return name + " " + UUID.randomUUID();
    }

    /**
     * 並べ替えの API に送る、今ある最上位のカテゴリの後ろに自分のカテゴリを並べた一覧を作る。
     *
     * <p>並べ替えの API は兄弟の全てを求める。
     *
     * @param others 今ある最上位のカテゴリの ID
     * @param mine   自分のカテゴリの ID
     * @return 送る ID の一覧
     */
    private static String[] withOthers(List<String> others, String... mine) {
        return Stream.concat(others.stream(), Arrays.stream(mine)).toArray(String[]::new);
    }

    /**
     * 各階層を並べ替えた木を作る。
     *
     * <p>最上位は「食品」「衣類」の順。「衣類」の子は「ボトムス」「トップス」の順で、「トップス」の子は「T シャツ」「シャツ」の順。
     * 最上位の名称はテストごとに変える。
     *
     * @return 作った木
     */
    private SortedTree sortedTree() throws Exception {
        var others = rootIds();
        var rootName = uniqueName(CLOTHING);
        var clothing = createdId(rootName, null);
        var food = createdId(uniqueName(FOOD), null);
        var tops = createdId(TOPS, clothing);
        var bottoms = createdId(BOTTOMS, clothing);
        createdId(T_SHIRT, tops);
        createdId(SHIRT, tops);
        reorder(null, withOthers(others, food, clothing)).andExpect(status().isNoContent());
        reorder(clothing, bottoms, tops).andExpect(status().isNoContent());
        return new SortedTree(rootName, clothing, food);
    }

    /**
     * 最上位のカテゴリ A・B・C を、この順に登録する（名称はテストごとに変える）。
     *
     * @return 3 つのカテゴリの ID
     */
    private Abc createAbc() throws Exception {
        return new Abc(createdId(uniqueName("A"), null), createdId(uniqueName("B"), null), createdId(uniqueName("C"), null));
    }

    /**
     * カテゴリを登録する。
     *
     * <p>応答は呼び出し側で確認する。
     *
     * @param name     名称
     * @param parentId 親のカテゴリ ID（最上位なら null）
     * @return 応答
     */
    private ResultActions createCategory(String name, String parentId) throws Exception {
        var parent = parentId == null ? "null" : "\"%s\"".formatted(parentId);
        return mockMvc.perform(post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "parentId": %s}""".formatted(name, parent)));
    }

    /**
     * カテゴリを登録して、その ID を返す。
     *
     * @param name     名称
     * @param parentId 親のカテゴリ ID（最上位なら null）
     * @return カテゴリ ID
     */
    private String createdId(String name, String parentId) throws Exception {
        var parent = parentId == null ? null : UUID.fromString(parentId);
        return catalog.category(name, parent).toString();
    }

    /**
     * カテゴリの名称を変える。
     *
     * @param id      カテゴリ ID
     * @param name    新しい名称
     * @param version 取得時のバージョン
     * @return 応答
     */
    private ResultActions rename(String id, String name, long version) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "version": %d}""".formatted(name, version)));
    }

    /**
     * 兄弟のカテゴリを、指定した順に並び替える。
     *
     * @param parentId    親のカテゴリ ID（最上位なら null）
     * @param categoryIds 並べたいカテゴリ ID
     * @return 応答
     */
    private ResultActions reorder(String parentId, String... categoryIds) throws Exception {
        var parent = parentId == null ? "null" : "\"%s\"".formatted(parentId);
        var ids = Arrays.stream(categoryIds).map("\"%s\""::formatted).collect(Collectors.joining(", "));
        return mockMvc.perform(put(BASE + "/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"parentId": %s, "categoryIds": [%s]}""".formatted(parent, ids)));
    }

    /**
     * 各階層を並べ替えた木
     *
     * @param rootName   最上位のカテゴリ「衣類」の名称（テストごとに変える）
     * @param clothingId 「衣類」の ID
     * @param foodId     「食品」の ID
     */
    private record SortedTree(String rootName, String clothingId, String foodId) {
    }

    /**
     * 最上位のカテゴリ A・B・C の ID
     *
     * @param a A の ID
     * @param b B の ID
     * @param c C の ID
     */
    private record Abc(String a, String b, String c) {
    }
}
