package com.ecsite.module.catalog.presentation;

import com.ecsite.support.MutableClock;
import com.ecsite.support.config.IntegrationTest;
import com.ecsite.support.data.CatalogTestData;
import com.ecsite.support.http.TestJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.ecsite.module.catalog.presentation.ProductRequests.BASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品 API の受け入れテスト（バリアント）
 *
 * <p>バリアントの登録・並び替え・削除・販売停止を確認する。商品の登録と編集は {@link ProductApiTest} が確認する。
 *
 * <p>現在時刻は {@link MutableClock} で固定する。税区分はテストごとに作る。テストごとにロールバックする。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る（準備した物から期待値を取り出さない）。
 */
@IntegrationTest
@Transactional
class ProductVariantApiTest {

    // ============================== 定数 ==============================

    /**
     * 存在しない ID
     */
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /**
     * 商品の品番
     */
    private static final String PRODUCT_CODE = "ABC-001";

    /**
     * 別の商品の品番
     */
    private static final String OTHER_PRODUCT_CODE = "ABC-002";

    /**
     * 色の軸
     */
    private static final String AXIS_COLOR = "色";

    /**
     * サイズの軸
     */
    private static final String AXIS_SIZE = "サイズ";

    /**
     * 白・M のバリアントの品番
     */
    private static final String WHITE_M_CODE = "ABC-001-WH-M";

    /**
     * 白
     */
    private static final String WHITE = "白";

    /**
     * 黒
     */
    private static final String BLACK = "黒";

    /**
     * M サイズ
     */
    private static final String SIZE_M = "M";

    /**
     * 素材の軸
     */
    private static final String AXIS_MATERIAL = "素材";

    /**
     * 綿
     */
    private static final String COTTON = "綿";

    /**
     * 品番の上限（文字）
     */
    private static final int CODE_MAX_LENGTH = 32;

    /**
     * 軸の値の上限（文字）
     */
    private static final int AXIS_VALUE_MAX_LENGTH = 50;

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    /**
     * 商品 API を呼ぶ補助
     */
    private ProductRequests products;

    @BeforeEach
    void setUp() throws Exception {
        var standard = new CatalogTestData(mockMvc, clock)
                .taxCategory("バリアント " + UUID.randomUUID().toString().substring(0, 8), "10").toString();
        products = new ProductRequests(mockMvc, standard);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("バリアントの登録")
    class Add {

        @Test
        @DisplayName("AC-276 登録すると 201 で、Location は作ったバリアントの URL")
        void returnsLocation() throws Exception {
            // 準備：色とサイズの軸を持つ商品
            var id = products.product(PRODUCT_CODE, AXIS_COLOR, AXIS_SIZE);

            // 操作と確認：201 で登録される
            var result = products.addVariant(id, WHITE_M_CODE, 0, WHITE, SIZE_M)
                    .andExpect(status().isCreated())
                    .andReturn();

            // 確認：Location が、本文の ID のバリアントの URL
            var variantId = TestJson.createdId(result.getResponse().getContentAsString());
            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                    .endsWith(BASE + "/" + id + "/variants/" + variantId);
        }

        @Test
        @DisplayName("AC-20 品番と軸の値を指定して登録できる")
        void addsVariant() throws Exception {
            // 準備：色とサイズの軸を持つ商品
            var id = products.product(PRODUCT_CODE, AXIS_COLOR, AXIS_SIZE);

            // 操作：白・M のバリアントを登録する
            var variantId = products.variantId(id, WHITE_M_CODE, 0, WHITE, SIZE_M);

            // 確認：商品の詳細に、指定した内容のバリアントがある
            assertWhiteMVariant(products.getProduct(id), variantId);
        }

        @Test
        @DisplayName("バリアントを登録すると、商品のバージョンが上がる")
        void incrementsVersion() throws Exception {
            // 準備：バージョン 0 の商品
            var id = products.product(PRODUCT_CODE, AXIS_COLOR, AXIS_SIZE);

            // 操作：バリアントを登録する
            products.addVariant(id, WHITE_M_CODE, 0, WHITE, SIZE_M).andExpect(status().isCreated());

            // 確認：バージョンが 1
            products.getProduct(id).andExpect(jsonPath("$.version").value(1));
        }

        @Test
        @DisplayName("AC-21 バリアント品番が別の商品と重複すれば 409")
        void rejectsDuplicatedVariantCode() throws Exception {
            // 準備：品番 V1 のバリアントを持つ商品と、別の商品
            var first = products.product(PRODUCT_CODE);
            products.addVariant(first, "V1", 0).andExpect(status().isCreated());
            var second = products.product(OTHER_PRODUCT_CODE);

            // 操作と確認：別の商品に同じ品番で登録すると、重複で拒否される
            products.addVariant(second, "V1", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("VARIANT_CODE_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("AC-21 バリアント品番の形式が違えば 400")
        void rejectsInvalidVariantCode() throws Exception {
            // 準備：商品
            var id = products.product(PRODUCT_CODE);

            // 操作と確認：英小文字の品番は、リクエストの誤りで拒否される
            products.addVariant(id, "v-1", 0)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-22 全ての軸の値を持たなければ 400")
        void rejectsMissingAxisValue() throws Exception {
            // 準備：色とサイズの軸を持つ商品
            var id = products.product(PRODUCT_CODE, AXIS_COLOR, AXIS_SIZE);

            // 操作と確認：色の値だけでは、軸の値の誤りで拒否される
            products.addVariant(id, "V1", 0, WHITE)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_AXIS_VALUES"));
        }

        @Test
        @DisplayName("AC-23 軸の値の組み合わせが重複すれば 409")
        void rejectsDuplicatedAxisValues() throws Exception {
            // 準備：白・M のバリアントを持つ商品
            var id = products.product(PRODUCT_CODE, AXIS_COLOR, AXIS_SIZE);
            products.addVariant(id, "V1", 0, WHITE, SIZE_M).andExpect(status().isCreated());

            // 操作と確認：同じ白・M の登録は、重複で拒否される
            products.addVariant(id, "V2", 1, WHITE, SIZE_M)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("VARIANT_AXIS_VALUES_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("AC-24 軸のない商品のバリアントは 1 つだけ。2 つ目は 400")
        void rejectsSecondVariantWithoutAxes() throws Exception {
            // 準備：軸がなく、バリアントを 1 つ持つ商品
            var id = products.product(PRODUCT_CODE);
            products.addVariant(id, "V1", 0).andExpect(status().isCreated());

            // 操作と確認：2 つ目の登録は拒否される
            products.addVariant(id, "V2", 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("SINGLE_VARIANT_ONLY"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1, CODE_MAX_LENGTH})
        @DisplayName("バリアント品番が範囲の端の文字数なら、その値で登録できる")
        void acceptsVariantCodeAtBoundary(int length) throws Exception {
            // 準備：軸のない商品
            var id = products.product(PRODUCT_CODE);

            // 操作：登録する
            products.addVariant(id, "V".repeat(length), 0).andExpect(status().isCreated());

            // 確認：送った品番で保存される
            products.getProduct(id).andExpect(jsonPath("$.variants[0].variantCode").value("V".repeat(length)));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, CODE_MAX_LENGTH + 1})
        @DisplayName("バリアント品番が範囲の外の文字数なら 400")
        void rejectsVariantCodeOutOfRange(int length) throws Exception {
            // 準備：軸のない商品
            var id = products.product(PRODUCT_CODE);

            // 操作と確認：リクエストの誤りで拒否される
            products.addVariant(id, "V".repeat(length), 0)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1, AXIS_VALUE_MAX_LENGTH})
        @DisplayName("軸の値が範囲の端の文字数なら、その値で登録できる")
        void acceptsAxisValueAtBoundary(int length) throws Exception {
            // 準備：色の軸を持つ商品
            var id = products.product(PRODUCT_CODE, AXIS_COLOR);

            // 操作：登録する
            products.addVariant(id, "V1", 0, "あ".repeat(length)).andExpect(status().isCreated());

            // 確認：送った軸の値で保存される
            products.getProduct(id).andExpect(jsonPath("$.variants[0].axisValues", contains("あ".repeat(length))));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, AXIS_VALUE_MAX_LENGTH + 1})
        @DisplayName("軸の値が範囲の外の文字数なら 400")
        void rejectsAxisValueOutOfRange(int length) throws Exception {
            // 準備：色の軸を持つ商品
            var id = products.product(PRODUCT_CODE, AXIS_COLOR);

            // 操作と確認：リクエストの誤りで拒否される
            products.addVariant(id, "V1", 0, "あ".repeat(length))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("軸の値は 3 つまで指定できる")
        void acceptsThreeAxisValues() throws Exception {
            // 準備：軸が 3 つの商品
            var id = products.product(PRODUCT_CODE, AXIS_COLOR, AXIS_SIZE, AXIS_MATERIAL);

            // 操作：登録する
            products.addVariant(id, "V1", 0, WHITE, SIZE_M, COTTON).andExpect(status().isCreated());

            // 確認：3 つの軸の値を、送った順に持つ
            products.getProduct(id).andExpect(jsonPath("$.variants[0].axisValues", contains(WHITE, SIZE_M, COTTON)));
        }

        @Test
        @DisplayName("軸の値が 4 つ以上なら 400")
        void rejectsFourAxisValues() throws Exception {
            // 準備：軸が 3 つの商品
            var id = products.product(PRODUCT_CODE, AXIS_COLOR, AXIS_SIZE, AXIS_MATERIAL);

            // 操作と確認：リクエストの誤りで拒否される
            products.addVariant(id, "V1", 0, WHITE, SIZE_M, COTTON, "無地")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("古いバージョンで登録すると 409")
        void rejectsVersionMismatch() throws Exception {
            // 準備：バリアントを 1 つ持ち、バージョンが 1 の商品
            var id = products.product(PRODUCT_CODE, AXIS_COLOR);
            products.variantId(id, "V-WH", 0, WHITE);

            // 操作と確認：古いバージョン 0 での登録は、競合で拒否される
            products.addVariant(id, "V-BK", 0, BLACK)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("存在しない商品へのバリアントの登録は 404")
        void rejectsUnknownProduct() throws Exception {
            // 操作と確認：見つからない
            products.addVariant(UNKNOWN_ID, "V1", 0)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("バリアントの並び替え")
    class Reorder {

        @Test
        @DisplayName("AC-27 表示順を変更できる")
        void reordersVariants() throws Exception {
            // 準備：白・黒の順にバリアントを持つ商品（バージョン 2）
            var id = products.product(PRODUCT_CODE, AXIS_COLOR);
            var white = products.variantId(id, "V-WH", 0, WHITE);
            var black = products.variantId(id, "V-BK", 1, BLACK);

            // 操作：黒・白の順に並び替える
            products.reorderVariants(id, 2, black, white).andExpect(status().isNoContent());

            // 確認：詳細のバリアントが黒・白の順
            products.getProduct(id).andExpect(jsonPath("$.variants[*].id", contains(black.toString(), white.toString())));
        }

        @Test
        @DisplayName("並び替えると、商品のバージョンが上がる")
        void incrementsVersion() throws Exception {
            // 準備：白・黒の順にバリアントを持つ商品（バージョン 2）
            var id = products.product(PRODUCT_CODE, AXIS_COLOR);
            var white = products.variantId(id, "V-WH", 0, WHITE);
            var black = products.variantId(id, "V-BK", 1, BLACK);

            // 操作：並び替える
            products.reorderVariants(id, 2, black, white).andExpect(status().isNoContent());

            // 確認：バージョンが 3
            products.getProduct(id).andExpect(jsonPath("$.version").value(3));
        }

        @Test
        @DisplayName("並びのバリアントが足りなければ 400")
        void rejectsMissingVariant() throws Exception {
            // 準備：白・黒のバリアントを持つ商品（バージョン 2）
            var id = products.product(PRODUCT_CODE, AXIS_COLOR);
            products.variantId(id, "V-WH", 0, WHITE);
            var black = products.variantId(id, "V-BK", 1, BLACK);

            // 操作と確認：黒だけの並びは、並びの誤りで拒否される
            products.reorderVariants(id, 2, black)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_VARIANT_ORDER"));
        }

        @Test
        @DisplayName("1 つだけの並びでも並び替えられる")
        void reordersSingleVariant() throws Exception {
            // 準備：軸がなく、バリアントを 1 つ持つ商品（バージョン 1）
            var id = products.product(PRODUCT_CODE);
            var only = products.variantId(id, "V1", 0);

            // 操作と確認：並び替えられる
            products.reorderVariants(id, 1, only).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("空の並びは 400")
        void rejectsEmptyOrder() throws Exception {
            // 準備：軸がなく、バリアントを 1 つ持つ商品（バージョン 1）
            var id = products.product(PRODUCT_CODE);
            products.variantId(id, "V1", 0);

            // 操作と確認：リクエストの誤りで拒否される
            products.reorderVariants(id, 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("古いバージョンで並び替えると 409")
        void rejectsVersionMismatch() throws Exception {
            // 準備：バリアントを 1 つ持ち、バージョンが 1 の商品
            var id = products.product(PRODUCT_CODE, AXIS_COLOR);
            var white = products.variantId(id, "V-WH", 0, WHITE);

            // 操作と確認：古いバージョン 0 での並び替えは、競合で拒否される
            products.reorderVariants(id, 0, white)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }
    }

    @Nested
    @DisplayName("バリアントの削除")
    class Delete {

        @Test
        @DisplayName("AC-71 C 側に表示されていない商品のバリアントは削除できる")
        void deletesVariantOfNeverShownProduct() throws Exception {
            // 準備：公開したことのない商品の、白のバリアント
            var id = products.product(PRODUCT_CODE, AXIS_COLOR);
            var white = products.variantId(id, "V-WH", 0, WHITE);

            // 操作：白のバリアントを削除する
            products.deleteVariant(id, white, 1).andExpect(status().isNoContent());

            // 確認：商品にバリアントがない
            products.getProduct(id).andExpect(jsonPath("$.variants").isEmpty());
        }

        @Test
        @DisplayName("AC-71 表示されたことのある商品のバリアントは削除できず 400")
        void rejectsDeletingVariantOfShownProduct() throws Exception {
            // 準備：白・黒のバリアントを持ち、公開した後に非公開にした商品（バージョン 4）
            var id = products.product(PRODUCT_CODE, AXIS_COLOR);
            var white = products.variantId(id, "V-WH", 0, WHITE);
            products.variantId(id, "V-BK", 1, BLACK);
            products.publish(id).andExpect(status().isNoContent());
            products.unpublish(id).andExpect(status().isNoContent());

            // 操作と確認：表示されたことがあるので拒否される
            products.deleteVariant(id, white, 4)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PRODUCT_ALREADY_SHOWN"));
        }

        @Test
        @DisplayName("バリアントを削除すると、商品のバージョンが上がる")
        void incrementsVersion() throws Exception {
            // 準備：白のバリアントを持ち、バージョンが 1 の商品
            var id = products.product(PRODUCT_CODE, AXIS_COLOR);
            var white = products.variantId(id, "V-WH", 0, WHITE);

            // 操作：白のバリアントを削除する
            products.deleteVariant(id, white, 1).andExpect(status().isNoContent());

            // 確認：バージョンが 2
            products.getProduct(id).andExpect(jsonPath("$.version").value(2));
        }

        @Test
        @DisplayName("古いバージョンで削除すると 409")
        void rejectsVersionMismatch() throws Exception {
            // 準備：バリアントを 1 つ持ち、バージョンが 1 の商品
            var id = products.product(PRODUCT_CODE, AXIS_COLOR);
            var white = products.variantId(id, "V-WH", 0, WHITE);

            // 操作と確認：古いバージョン 0 での削除は、競合で拒否される
            products.deleteVariant(id, white, 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("存在しないバリアントの削除は 404")
        void rejectsUnknownVariant() throws Exception {
            // 準備：商品
            var id = products.product(PRODUCT_CODE);

            // 操作と確認：見つからない
            products.deleteVariant(id, UNKNOWN_ID, 0)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("バリアントの販売停止")
    class SalesSuspension {

        @Test
        @DisplayName("AC-28 販売停止にできる")
        void suspends() throws Exception {
            // 準備：販売中のバリアント
            var id = products.product(PRODUCT_CODE);
            var variantId = products.variantId(id, "V1", 0);

            // 操作：販売停止にする
            products.suspend(id, variantId).andExpect(status().isNoContent());

            // 確認：販売停止中
            products.getProduct(id).andExpect(jsonPath("$.variants[0].salesSuspended").value(true));
        }

        @Test
        @DisplayName("販売停止にすると、商品のバージョンが上がる")
        void incrementsVersionOnSuspend() throws Exception {
            // 準備：販売中のバリアントを持ち、バージョンが 1 の商品
            var id = products.product(PRODUCT_CODE);
            var variantId = products.variantId(id, "V1", 0);

            // 操作：販売停止にする
            products.suspend(id, variantId).andExpect(status().isNoContent());

            // 確認：バージョンが 2
            products.getProduct(id).andExpect(jsonPath("$.version").value(2));
        }

        @Test
        @DisplayName("販売停止中のバリアントを販売停止にしても 204 で、何も変わらない")
        void suspendsIdempotently() throws Exception {
            // 準備：販売停止にして、バージョンが 2 になった商品
            var id = products.product(PRODUCT_CODE);
            var variantId = products.variantId(id, "V1", 0);
            products.suspend(id, variantId).andExpect(status().isNoContent());

            // 操作：もう一度販売停止にする
            products.suspend(id, variantId).andExpect(status().isNoContent());

            // 確認：バージョンが 2 のまま
            products.getProduct(id).andExpect(jsonPath("$.version").value(2));
        }

        @Test
        @DisplayName("AC-28 販売停止を解除できる")
        void resumes() throws Exception {
            // 準備：販売停止中のバリアント
            var id = products.product(PRODUCT_CODE);
            var variantId = products.variantId(id, "V1", 0);
            products.suspend(id, variantId).andExpect(status().isNoContent());

            // 操作：販売停止を解除する
            products.resume(id, variantId).andExpect(status().isNoContent());

            // 確認：販売中
            products.getProduct(id).andExpect(jsonPath("$.variants[0].salesSuspended").value(false));
        }

        @Test
        @DisplayName("販売停止を解除すると、商品のバージョンが上がる")
        void incrementsVersionOnResume() throws Exception {
            // 準備：販売停止にして、バージョンが 2 になった商品
            var id = products.product(PRODUCT_CODE);
            var variantId = products.variantId(id, "V1", 0);
            products.suspend(id, variantId).andExpect(status().isNoContent());

            // 操作：販売停止を解除する
            products.resume(id, variantId).andExpect(status().isNoContent());

            // 確認：バージョンが 3
            products.getProduct(id).andExpect(jsonPath("$.version").value(3));
        }

        @Test
        @DisplayName("存在しない商品のバリアントの販売停止は 404")
        void rejectsUnknownProduct() throws Exception {
            // 操作と確認：見つからない
            products.suspend(UNKNOWN_ID, UNKNOWN_ID).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("存在しないバリアントの販売停止は 404")
        void rejectsSuspendingUnknownVariant() throws Exception {
            // 準備：商品
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作と確認：見つからない
            products.suspend(id, UNKNOWN_ID)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("存在しないバリアントの販売停止の解除は 404")
        void rejectsResumingUnknownVariant() throws Exception {
            // 準備：商品
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作と確認：見つからない
            products.resume(id, UNKNOWN_ID).andExpect(status().isNotFound());
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 商品の詳細に、白・M のバリアントが登録されていることを確認する。
     *
     * <p>確認すること（ID のほかの期待値は定数）：
     * <ul>
     *   <li>バリアント ID</li>
     *   <li>バリアント品番</li>
     *   <li>軸の値（並びを含む）</li>
     *   <li>販売停止でない</li>
     * </ul>
     *
     * @param detail    商品の詳細の応答
     * @param variantId 登録の応答のバリアント ID（実行ごとに作るので、定数にできない）
     */
    private static void assertWhiteMVariant(ResultActions detail, UUID variantId) throws Exception {
        detail.andExpect(jsonPath("$.variants[0].id").value(variantId.toString()))
                .andExpect(jsonPath("$.variants[0].variantCode").value(WHITE_M_CODE))
                .andExpect(jsonPath("$.variants[0].axisValues", contains(WHITE, SIZE_M)))
                .andExpect(jsonPath("$.variants[0].salesSuspended").value(false));
    }
}
