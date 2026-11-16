package com.ecsite.module.catalog.presentation;

import com.ecsite.module.catalog.infrastructure.directory.TaxRateScheduleCaches;
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
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.ecsite.module.catalog.presentation.ProductRequests.BASE;
import static com.ecsite.module.catalog.presentation.ProductRequests.productJson;
import static com.ecsite.module.catalog.presentation.ProductRequests.updateJson;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品 API の受け入れテスト（商品の登録・取得・編集）
 *
 * <p>バリアントは {@link ProductVariantApiTest}、公開・非公開・販売終了・削除・公開期間は {@link ProductStatusApiTest} が確認する。
 *
 * <p>現在時刻は {@link MutableClock} で固定する。税区分（標準 10%、軽減 8%、有効な税率のない税区分）とカテゴリは、
 * テストごとに作る。テストごとにロールバックする。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る（準備した物から期待値を取り出さない）。
 */
@IntegrationTest
@Transactional
class ProductApiTest {

    // ============================== 定数 ==============================

    /**
     * 存在しない ID
     */
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /**
     * 有効な税率がない税区分の税率が有効になる日時
     *
     * <p>日本時間の 2027-04-01 00:00。
     */
    private static final Instant NOT_YET_EFFECTIVE_FROM = OffsetDateTime.parse("2027-04-01T00:00:00+09:00").toInstant();

    /**
     * 登録する商品の品番
     */
    private static final String PRODUCT_CODE = "ABC-001";

    /**
     * 登録する商品の商品名
     */
    private static final String NAME = "T シャツ";

    /**
     * 登録する商品の説明
     */
    private static final String DESCRIPTION = "綿 100%";

    /**
     * 登録する商品の税抜価格（円）
     */
    private static final int PRICE_EXCL_TAX = 1000;

    /**
     * 登録する商品の税込価格（円）
     *
     * <p>標準の税率 10% で計算したもの。
     */
    private static final int PRICE_INCL_TAX = 1100;

    /**
     * 色の軸
     */
    private static final String AXIS_COLOR = "色";

    /**
     * サイズの軸
     */
    private static final String AXIS_SIZE = "サイズ";

    /**
     * 素材の軸
     */
    private static final String AXIS_MATERIAL = "素材";

    /**
     * 編集後の商品名
     */
    private static final String NEW_NAME = "ポロシャツ";

    /**
     * 編集後の説明
     */
    private static final String NEW_DESCRIPTION = "綿 50%";

    /**
     * 編集後の税抜価格（円）
     */
    private static final int NEW_PRICE_EXCL_TAX = 2000;

    /**
     * 編集後の税込価格（円）
     *
     * <p>標準の税率 10% で計算したもの。
     */
    private static final int NEW_PRICE_INCL_TAX = 2200;

    /**
     * 軽減の税区分に変えた後の税込価格（円）
     *
     * <p>税抜価格 1,000 円を、軽減の税率 8% で計算したもの。
     */
    private static final int REDUCED_PRICE_INCL_TAX = 1080;

    /**
     * 商品名の上限（文字）
     */
    private static final int NAME_MAX_LENGTH = 200;

    /**
     * 説明の上限（文字）
     */
    private static final int DESCRIPTION_MAX_LENGTH = 10_000;

    /**
     * 軸の名前の上限（文字）
     */
    private static final int AXIS_NAME_MAX_LENGTH = 30;

    /**
     * 商品品番の上限（文字）
     */
    private static final int CODE_MAX_LENGTH = 32;

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    ApplicationContext context;

    /**
     * 標準
     *
     * <p>税率は 10%。
     */
    private String standard;

    /**
     * 軽減
     *
     * <p>税率は 8%。
     */
    private String reduced;

    /**
     * 有効な税率がまだない税区分
     *
     * <p>2027-04-01 から 10%。
     */
    private String notYetEffective;

    /**
     * 衣類のカテゴリ
     */
    private String clothing;

    /**
     * 税区分とカテゴリを作る補助
     */
    private CatalogTestData catalog;

    /**
     * 商品 API を呼ぶ補助
     */
    private ProductRequests products;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new CatalogTestData(mockMvc, clock);
        standard = catalog.taxCategory("標準 " + UUID.randomUUID().toString().substring(0, 8), "10").toString();
        reduced = catalog.taxCategory("軽減", "8").toString();
        notYetEffective = catalog.taxCategory("将来", "10", NOT_YET_EFFECTIVE_FROM).toString();
        clothing = catalog.category("衣類 " + UUID.randomUUID(), null).toString();
        products = new ProductRequests(mockMvc, standard);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("商品の登録")
    class Create {

        @Test
        @DisplayName("AC-13 登録すると 201 で、Location の URL で登録した商品を取得できる")
        void createsWithLocation() throws Exception {
            // 操作と確認：201 で、Location が商品の URL
            var result = products.create(fullProductJson())
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*" + BASE + "/[0-9a-f-]{36}")))
                    .andReturn();
            var id = TestJson.createdId(result.getResponse().getContentAsString());

            // 確認：Location の URL で、本文の ID の商品を取得できる
            mockMvc.perform(get(result.getResponse().getHeader(HttpHeaders.LOCATION)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()));
        }

        @Test
        @DisplayName("AC-13 指定した内容で登録される")
        void createsWithSpecifiedContent() throws Exception {
            // 操作：全ての項目を指定して登録する
            var id = products.createdId(fullProductJson());

            // 確認：詳細の項目が、指定した内容と同じ
            assertSpecifiedContent(products.getProduct(id), standard, clothing);
        }

        @Test
        @DisplayName("登録直後の商品は、バリアントを持たない")
        void startsWithoutVariants() throws Exception {
            // 操作：商品を登録する
            var id = products.createdId(fullProductJson());

            // 確認：バリアントがない
            products.getProduct(id).andExpect(jsonPath("$.variants").isEmpty());
        }

        @Test
        @DisplayName("登録直後の商品は、バージョンが 0")
        void startsAtVersionZero() throws Exception {
            // 操作：商品を登録する
            var id = products.createdId(fullProductJson());

            // 確認：バージョンが 0
            products.getProduct(id).andExpect(jsonPath("$.version").value(0));
        }

        @Test
        @DisplayName("登録直後の商品は、登録日時と更新日時を持つ")
        void startsWithTimestamps() throws Exception {
            // 操作：商品を登録する
            var id = products.createdId(fullProductJson());

            // 確認：登録日時と更新日時がある
            products.getProduct(id)
                    .andExpect(jsonPath("$.createdAt").isString())
                    .andExpect(jsonPath("$.updatedAt").isString());
        }

        @Test
        @DisplayName("AC-17 登録直後の商品は非公開で、初回表示日時を持たない")
        void startsUnpublished() throws Exception {
            // 操作：商品を登録する
            var id = products.createdId(fullProductJson());

            // 確認：非公開で、C 側に表示されたことがない
            products.getProduct(id)
                    .andExpect(jsonPath("$.status").value("UNPUBLISHED"))
                    .andExpect(jsonPath("$.firstShownAt").value(nullValue()));
        }

        @Test
        @DisplayName("AC-13 所属カテゴリと軸は省略でき、空になる")
        void createsWithoutCategoriesAndAxes() throws Exception {
            // 操作：所属カテゴリと軸を省いて登録する
            var id = products.product(PRODUCT_CODE);

            // 確認：所属カテゴリと軸が空
            products.getProduct(id)
                    .andExpect(jsonPath("$.categoryIds").isEmpty())
                    .andExpect(jsonPath("$.axes").isEmpty());
        }

        @Test
        @DisplayName("AC-13 所属カテゴリを複数指定できる")
        void createsWithMultipleCategories() throws Exception {
            // 準備：衣類のほかに、もう 1 つのカテゴリ
            var food = catalog.category("食品 " + UUID.randomUUID(), null).toString();

            // 操作：2 つのカテゴリを指定して登録する
            var id = products.createdId("""
                    {"productCode": "%s", "name": "%s", "description": "", "taxCategoryId": "%s",
                     "priceExclTax": %d, "categoryIds": ["%s", "%s"]}"""
                    .formatted(PRODUCT_CODE, NAME, standard, PRICE_EXCL_TAX, clothing, food));

            // 確認：両方のカテゴリに所属する
            products.getProduct(id).andExpect(jsonPath("$.categoryIds", containsInAnyOrder(clothing, food)));
        }

        @Test
        @DisplayName("AC-13 軸は 3 つまで指定できる")
        void createsWithThreeAxes() throws Exception {
            // 操作：軸を 3 つ指定して登録する
            var id = products.product(PRODUCT_CODE, AXIS_COLOR, AXIS_SIZE, AXIS_MATERIAL);

            // 確認：3 つの軸を、指定した順に持つ
            products.getProduct(id).andExpect(jsonPath("$.axes", contains(AXIS_COLOR, AXIS_SIZE, AXIS_MATERIAL)));
        }

        @Test
        @DisplayName("AC-13 軸が 4 つ以上なら 400")
        void rejectsFourAxes() throws Exception {
            // 操作と確認：軸を 4 つ指定すると、リクエストの誤りで拒否される
            products.create("""
                            {"productCode": "%s", "name": "%s", "description": "", "taxCategoryId": "%s",
                             "priceExclTax": %d, "axes": ["%s", "%s", "%s", "柄"]}"""
                            .formatted(PRODUCT_CODE, NAME, standard, PRICE_EXCL_TAX, AXIS_COLOR, AXIS_SIZE, AXIS_MATERIAL))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-14 商品品番が重複すれば 409")
        void rejectsDuplicatedProductCode() throws Exception {
            // 準備：同じ品番の商品
            products.product(PRODUCT_CODE);

            // 操作と確認：同じ品番での登録は、重複で拒否される
            products.create(productJson(PRODUCT_CODE, standard, PRICE_EXCL_TAX))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("PRODUCT_CODE_ALREADY_EXISTS"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"abc-001", "-ABC", "ABC-", "ABC--1"})
        @DisplayName("AC-15 商品品番の形式が違えば 400")
        void rejectsInvalidProductCode(String code) throws Exception {
            // 操作と確認：リクエストの誤りで拒否される
            products.create(productJson(code, standard, PRICE_EXCL_TAX))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-16 税区分がなければ 400")
        void rejectsMissingTaxCategory() throws Exception {
            // 操作と確認：税区分を省くと、リクエストの誤りで拒否される
            products.create("""
                            {"productCode": "%s", "name": "%s", "description": "", "priceExclTax": %d}"""
                            .formatted(PRODUCT_CODE, NAME, PRICE_EXCL_TAX))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-6 現時点で有効な税率のない税区分は 400")
        void rejectsTaxCategoryWithoutCurrentRate() throws Exception {
            // 操作と確認：割り当てられない税区分として拒否される
            products.create(productJson(PRODUCT_CODE, notYetEffective, PRICE_EXCL_TAX))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_CATEGORY_NOT_ASSIGNABLE"));
        }

        @Test
        @DisplayName("AC-6 存在しない税区分は 400")
        void rejectsUnknownTaxCategory() throws Exception {
            // 操作と確認：割り当てられない税区分として拒否される
            products.create(productJson(PRODUCT_CODE, UNKNOWN_ID.toString(), PRICE_EXCL_TAX))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_CATEGORY_NOT_ASSIGNABLE"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(longs = {1, 9_999_999})
        @DisplayName("AC-25 税抜価格が範囲の端なら登録できる")
        void acceptsPriceAtBoundary(long price) throws Exception {
            // 操作と確認：登録できる
            products.create(productJson(PRODUCT_CODE, standard, price)).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-25 税抜価格が 1 円未満なら 400")
        void rejectsPriceLessThanOneYen() throws Exception {
            // 操作と確認：0 円は、リクエストの誤りで拒否される
            products.create(productJson(PRODUCT_CODE, standard, 0))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-25 税抜価格が 9,999,999 円を超えれば 400")
        void rejectsPriceOverMaximum() throws Exception {
            // 操作と確認：10,000,000 円は、リクエストの誤りで拒否される
            products.create(productJson(PRODUCT_CODE, standard, 10_000_000))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-25 税抜価格が long の上限でも、500 でなく 400")
        void rejectsPriceAtLongMaximum() throws Exception {
            // 操作と確認：拒否される
            products.create(productJson(PRODUCT_CODE, standard, Long.MAX_VALUE)).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("存在しないカテゴリを指定すれば 400")
        void rejectsUnknownCategory() throws Exception {
            // 操作と確認：カテゴリの誤りで拒否される
            products.create("""
                            {"productCode": "%s", "name": "%s", "description": "", "taxCategoryId": "%s",
                             "priceExclTax": %d, "categoryIds": ["%s"]}"""
                            .formatted(PRODUCT_CODE, NAME, standard, PRICE_EXCL_TAX, UNKNOWN_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CATEGORY_ID"));
        }
    }

    @Nested
    @DisplayName("登録の入力の範囲")
    class CreateRanges {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1, NAME_MAX_LENGTH})
        @DisplayName("商品名が範囲の端の文字数なら登録できる")
        void acceptsNameAtBoundary(int length) throws Exception {
            // 操作と確認：登録できる
            products.create(createJson(PRODUCT_CODE, "あ".repeat(length), "", AXIS_COLOR)).andExpect(status().isCreated());
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, NAME_MAX_LENGTH + 1})
        @DisplayName("商品名が範囲の外の文字数なら 400")
        void rejectsNameOutOfRange(int length) throws Exception {
            // 操作と確認：リクエストの誤りで拒否される
            products.create(createJson(PRODUCT_CODE, "あ".repeat(length), "", AXIS_COLOR))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, DESCRIPTION_MAX_LENGTH})
        @DisplayName("説明が範囲の端の文字数なら登録できる")
        void acceptsDescriptionAtBoundary(int length) throws Exception {
            // 操作と確認：登録できる
            products.create(createJson(PRODUCT_CODE, NAME, "あ".repeat(length), AXIS_COLOR)).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("説明が上限を 1 文字超えれば 400")
        void rejectsDescriptionOverMaximum() throws Exception {
            // 操作と確認：リクエストの誤りで拒否される
            products.create(createJson(PRODUCT_CODE, NAME, "あ".repeat(DESCRIPTION_MAX_LENGTH + 1), AXIS_COLOR))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1, AXIS_NAME_MAX_LENGTH})
        @DisplayName("軸の名前が範囲の端の文字数なら登録できる")
        void acceptsAxisNameAtBoundary(int length) throws Exception {
            // 操作と確認：登録できる
            products.create(createJson(PRODUCT_CODE, NAME, "", "あ".repeat(length))).andExpect(status().isCreated());
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, AXIS_NAME_MAX_LENGTH + 1})
        @DisplayName("軸の名前が範囲の外の文字数なら 400")
        void rejectsAxisNameOutOfRange(int length) throws Exception {
            // 操作と確認：リクエストの誤りで拒否される
            products.create(createJson(PRODUCT_CODE, NAME, "", "あ".repeat(length)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1, CODE_MAX_LENGTH})
        @DisplayName("商品品番が範囲の端の文字数なら登録できる")
        void acceptsProductCodeAtBoundary(int length) throws Exception {
            // 操作と確認：登録できる
            products.create(createJson("A".repeat(length), NAME, "", AXIS_COLOR)).andExpect(status().isCreated());
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, CODE_MAX_LENGTH + 1})
        @DisplayName("商品品番が範囲の外の文字数なら 400")
        void rejectsProductCodeOutOfRange(int length) throws Exception {
            // 操作と確認：リクエストの誤りで拒否される
            products.create(createJson("A".repeat(length), NAME, "", AXIS_COLOR))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
    }

    @Nested
    @DisplayName("編集の入力の範囲")
    class UpdateRanges {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {1, NAME_MAX_LENGTH})
        @DisplayName("商品名が範囲の端の文字数なら編集できる")
        void acceptsNameAtBoundary(int length) throws Exception {
            // 準備：商品
            var id = products.product(PRODUCT_CODE);

            // 操作と確認：編集できる
            products.update(id, nameAndDescriptionJson("あ".repeat(length), "")).andExpect(status().isNoContent());
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, NAME_MAX_LENGTH + 1})
        @DisplayName("商品名が範囲の外の文字数なら 400")
        void rejectsNameOutOfRange(int length) throws Exception {
            // 準備：商品
            var id = products.product(PRODUCT_CODE);

            // 操作と確認：リクエストの誤りで拒否される
            products.update(id, nameAndDescriptionJson("あ".repeat(length), ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(ints = {0, DESCRIPTION_MAX_LENGTH})
        @DisplayName("説明が範囲の端の文字数なら編集できる")
        void acceptsDescriptionAtBoundary(int length) throws Exception {
            // 準備：商品
            var id = products.product(PRODUCT_CODE);

            // 操作と確認：編集できる
            products.update(id, nameAndDescriptionJson(NAME, "あ".repeat(length))).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("説明が上限を 1 文字超えれば 400")
        void rejectsDescriptionOverMaximum() throws Exception {
            // 準備：商品
            var id = products.product(PRODUCT_CODE);

            // 操作と確認：リクエストの誤りで拒否される
            products.update(id, nameAndDescriptionJson(NAME, "あ".repeat(DESCRIPTION_MAX_LENGTH + 1)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(longs = {1, 9_999_999})
        @DisplayName("AC-25 税抜価格が範囲の端なら編集できる")
        void acceptsPriceAtBoundary(long price) throws Exception {
            // 準備：商品
            var id = products.product(PRODUCT_CODE);

            // 操作と確認：編集できる
            products.update(id, updateJson(standard, price, 0)).andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("商品の取得")
    class Get {

        @Test
        @DisplayName("存在しない ID は 404")
        void returnsNotFound() throws Exception {
            // 操作と確認：見つからない
            products.getProduct(UNKNOWN_ID)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("有効な税率のない税区分の商品も取得でき、税込価格は空")
        void returnsNoPriceInclTaxWithoutTaxRate() throws Exception {
            // 準備：税区分に今は有効な税率がない商品
            var id = productBeforeRateStarts();

            // 操作と確認：200 で、税抜価格はあり、税込価格は空
            products.getProduct(id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.priceExclTax").value(PRICE_EXCL_TAX))
                    .andExpect(jsonPath("$.priceInclTax").value(nullValue()));
        }
    }

    @Nested
    @DisplayName("商品の編集")
    class Update {

        @Test
        @DisplayName("AC-18 商品名・説明・税抜価格・所属カテゴリを変更できる")
        void updatesFields() throws Exception {
            // 準備：所属カテゴリのない商品
            var id = products.product(PRODUCT_CODE);

            // 操作：商品名・説明・税抜価格・所属カテゴリを変えて編集する
            updateProduct(id).andExpect(status().isNoContent());

            // 確認：詳細の項目が、編集した内容と同じ
            assertUpdatedContent(products.getProduct(id), clothing);
        }

        @Test
        @DisplayName("AC-18 税区分を変更できる")
        void updatesTaxCategory() throws Exception {
            // 準備：標準の税区分の商品
            var id = products.product(PRODUCT_CODE);

            // 操作：軽減の税区分に変える
            products.update(id, updateJson(reduced, PRICE_EXCL_TAX, 0)).andExpect(status().isNoContent());

            // 確認：税区分が軽減
            products.getProduct(id).andExpect(jsonPath("$.taxCategoryId").value(reduced));
        }

        @Test
        @DisplayName("AC-15 編集しても、商品品番は変わらない")
        void keepsProductCode() throws Exception {
            // 準備：商品
            var id = products.product(PRODUCT_CODE);

            // 操作：編集する
            updateProduct(id).andExpect(status().isNoContent());

            // 確認：品番は登録したまま
            products.getProduct(id).andExpect(jsonPath("$.productCode").value(PRODUCT_CODE));
        }

        @Test
        @DisplayName("税抜価格を変えると、税込価格が変わる")
        void recalculatesPriceInclTax() throws Exception {
            // 準備：商品
            var id = products.product(PRODUCT_CODE);

            // 操作：税抜価格を変えて編集する
            updateProduct(id).andExpect(status().isNoContent());

            // 確認：税込価格が、変えた後の税抜価格から計算される
            products.getProduct(id).andExpect(jsonPath("$.priceInclTax").value(NEW_PRICE_INCL_TAX));
        }

        @Test
        @DisplayName("編集すると、バージョンが上がる")
        void incrementsVersion() throws Exception {
            // 準備：バージョン 0 の商品
            var id = products.product(PRODUCT_CODE);

            // 操作：編集する
            updateProduct(id).andExpect(status().isNoContent());

            // 確認：バージョンが 1
            products.getProduct(id).andExpect(jsonPath("$.version").value(1));
        }

        @Test
        @DisplayName("AC-19 税区分を変えると、税込価格が変更後の税率で計算される")
        void recalculatesPriceInclTaxWithNewTaxCategory() throws Exception {
            // 準備：標準の税区分の商品
            var id = products.product(PRODUCT_CODE);

            // 確認（前提）：税込価格が標準の税率で計算されている
            products.getProduct(id).andExpect(jsonPath("$.priceInclTax").value(PRICE_INCL_TAX));

            // 操作：税抜価格を変えずに、軽減の税区分に変える
            products.update(id, updateJson(reduced, PRICE_EXCL_TAX, 0)).andExpect(status().isNoContent());

            // 確認：税込価格が軽減の税率で計算される
            products.getProduct(id).andExpect(jsonPath("$.priceInclTax").value(REDUCED_PRICE_INCL_TAX));
        }

        @Test
        @DisplayName("AC-6 現時点で有効な税率のない税区分に変えると 400")
        void rejectsTaxCategoryWithoutCurrentRate() throws Exception {
            // 準備：商品
            var id = products.product(PRODUCT_CODE);

            // 操作と確認：割り当てられない税区分として拒否される
            products.update(id, updateJson(notYetEffective, PRICE_EXCL_TAX, 0))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_CATEGORY_NOT_ASSIGNABLE"));
        }

        @Test
        @DisplayName("AC-6 税区分に今は有効な税率がなくても、税区分を変えない編集はできる")
        void updatesWithoutChangingTaxCategoryWithoutCurrentRate() throws Exception {
            // 準備：税区分に今は有効な税率がない商品
            var id = productBeforeRateStarts();

            // 操作：税区分を変えずに、税抜価格を変えて編集する
            products.update(id, updateJson(notYetEffective, NEW_PRICE_EXCL_TAX, 0)).andExpect(status().isNoContent());

            // 確認：税抜価格が変わる
            products.getProduct(id).andExpect(jsonPath("$.priceExclTax").value(NEW_PRICE_EXCL_TAX));
        }

        @Test
        @DisplayName("存在しないカテゴリを指定すると 400 で、商品は変わらない")
        void rejectsUnknownCategoryOnUpdate() throws Exception {
            // 準備：所属カテゴリのない商品
            var id = products.product(PRODUCT_CODE);

            // 操作と確認：カテゴリの誤りで拒否される
            products.update(id, """
                            {"name": "%s", "description": "", "taxCategoryId": "%s", "priceExclTax": %d,
                             "categoryIds": ["%s"], "version": 0}""".formatted(NEW_NAME, standard, NEW_PRICE_EXCL_TAX, UNKNOWN_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CATEGORY_ID"));

            // 確認：商品名・所属カテゴリ・バージョンは変わらない
            products.getProduct(id)
                    .andExpect(jsonPath("$.name").value(ProductRequests.DEFAULT_NAME))
                    .andExpect(jsonPath("$.categoryIds").isEmpty())
                    .andExpect(jsonPath("$.version").value(0));
        }

        @Test
        @DisplayName("AC-25 税抜価格が 1 円未満なら 400")
        void rejectsPriceLessThanOneYen() throws Exception {
            // 準備：商品
            var id = products.product(PRODUCT_CODE);

            // 操作と確認：0 円は、リクエストの誤りで拒否される
            products.update(id, updateJson(standard, 0, 0))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-25 税抜価格が 9,999,999 円を超えれば 400")
        void rejectsPriceOverMaximum() throws Exception {
            // 準備：商品
            var id = products.product(PRODUCT_CODE);

            // 操作と確認：10,000,000 円は、リクエストの誤りで拒否される
            products.update(id, updateJson(standard, 10_000_000, 0))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("取得時のバージョンと一致しなければ 409")
        void rejectsVersionMismatch() throws Exception {
            // 準備：1 回編集して、バージョンが 1 になった商品
            var id = products.product(PRODUCT_CODE);
            products.update(id, updateJson(standard, 2000, 0)).andExpect(status().isNoContent());

            // 操作と確認：古いバージョン 0 での編集は、競合で拒否される
            products.update(id, updateJson(standard, 3000, 0))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("存在しない商品は 404")
        void rejectsUnknownProduct() throws Exception {
            // 操作と確認：見つからない
            products.update(UNKNOWN_ID, updateJson(standard, PRICE_EXCL_TAX, 0))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("商品が所属しているカテゴリの削除")
    class AssignedCategory {

        @Test
        @DisplayName("AC-12 商品が所属しているカテゴリは削除できず 400")
        void rejectsDeletingCategoryWithProducts() throws Exception {
            // 準備：衣類に所属する商品
            productInClothing();

            // 操作と確認：衣類の削除は、商品があるので拒否される
            mockMvc.perform(delete("/api/admin/categories/" + clothing))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_HAS_PRODUCTS"));
        }

        @Test
        @DisplayName("AC-12 商品の所属を外せば、カテゴリを削除できる")
        void deletesCategoryAfterUnassigning() throws Exception {
            // 準備：衣類に所属していた商品を、編集で所属から外す
            var id = productInClothing();
            products.update(id, updateJson(standard, PRICE_EXCL_TAX, 0)).andExpect(status().isNoContent());

            // 操作と確認：衣類を削除できる
            mockMvc.perform(delete("/api/admin/categories/" + clothing)).andExpect(status().isNoContent());
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 詳細が、{@link #fullProductJson()} で指定した内容であることを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>商品品番</li>
     *   <li>商品名</li>
     *   <li>説明</li>
     *   <li>税区分</li>
     *   <li>税抜価格</li>
     *   <li>税込価格（標準の税率で計算したもの）</li>
     *   <li>所属カテゴリ</li>
     *   <li>軸（並びを含む）</li>
     * </ul>
     *
     * @param detail        商品の詳細の応答
     * @param taxCategoryId 指定した税区分の ID（テストごとに作るので、定数にできない）
     * @param categoryId    指定したカテゴリの ID（テストごとに作るので、定数にできない）
     */
    private static void assertSpecifiedContent(ResultActions detail, String taxCategoryId, String categoryId) throws Exception {
        detail.andExpect(jsonPath("$.productCode").value(PRODUCT_CODE))
                .andExpect(jsonPath("$.name").value(NAME))
                .andExpect(jsonPath("$.description").value(DESCRIPTION))
                .andExpect(jsonPath("$.taxCategoryId").value(taxCategoryId))
                .andExpect(jsonPath("$.priceExclTax").value(PRICE_EXCL_TAX))
                .andExpect(jsonPath("$.priceInclTax").value(PRICE_INCL_TAX))
                .andExpect(jsonPath("$.categoryIds", contains(categoryId)))
                .andExpect(jsonPath("$.axes", contains(AXIS_COLOR, AXIS_SIZE)));
    }

    /**
     * 詳細が、{@link #updateProduct(UUID)} で編集した内容であることを確認する。
     *
     * <p>確認すること（期待値は定数）：
     * <ul>
     *   <li>商品名</li>
     *   <li>説明</li>
     *   <li>税抜価格</li>
     *   <li>所属カテゴリ</li>
     * </ul>
     *
     * @param detail     商品の詳細の応答
     * @param categoryId 編集で指定したカテゴリの ID（テストごとに作るので、定数にできない）
     */
    private static void assertUpdatedContent(ResultActions detail, String categoryId) throws Exception {
        detail.andExpect(jsonPath("$.name").value(NEW_NAME))
                .andExpect(jsonPath("$.description").value(NEW_DESCRIPTION))
                .andExpect(jsonPath("$.priceExclTax").value(NEW_PRICE_EXCL_TAX))
                .andExpect(jsonPath("$.categoryIds", contains(categoryId)));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 有効な税率がまだない税区分の商品を登録し、その税区分に今は有効な税率がない状態にする。
     *
     * <p>最後の税率は無期限なので、商品を登録した後に有効な税率がなくなるのは、最初の税率の開始より前の時刻だけ。
     * そこで、税率の開始の時刻に商品を登録した後、時計を開始の 1 秒前に戻して、税率のキャッシュを捨てる。
     *
     * @return 商品 ID
     */
    private UUID productBeforeRateStarts() throws Exception {
        clock.setInstant(NOT_YET_EFFECTIVE_FROM);
        var id = products.createdId(productJson(PRODUCT_CODE, notYetEffective, PRICE_EXCL_TAX));
        clock.setInstant(NOT_YET_EFFECTIVE_FROM.minusSeconds(1));
        TaxRateScheduleCaches.evict(context);
        return id;
    }

    /**
     * 全ての項目を指定した、商品の登録の本文を作る。
     *
     * <p>標準の税区分、衣類のカテゴリ、色とサイズの軸。
     *
     * @return 本文
     */
    private String fullProductJson() {
        return """
                {"productCode": "%s", "name": "%s", "description": "%s", "taxCategoryId": "%s", "priceExclTax": %d,
                 "categoryIds": ["%s"], "axes": ["%s", "%s"]}"""
                .formatted(PRODUCT_CODE, NAME, DESCRIPTION, standard, PRICE_EXCL_TAX, clothing, AXIS_COLOR, AXIS_SIZE);
    }

    /**
     * 品番・商品名・説明・軸を指定した、商品の登録の本文を作る（標準の税区分、所属カテゴリなし）。
     *
     * @param code        商品品番
     * @param name        商品名
     * @param description 説明
     * @param axis        軸の名前（1 つ）
     * @return 本文
     */
    private String createJson(String code, String name, String description, String axis) {
        return """
                {"productCode": "%s", "name": "%s", "description": "%s", "taxCategoryId": "%s", "priceExclTax": %d,
                 "axes": ["%s"]}"""
                .formatted(code, name, description, standard, PRICE_EXCL_TAX, axis);
    }

    /**
     * バージョン 0 の商品の、商品名と説明を指定した編集の本文を作る（標準の税区分、所属カテゴリなし）。
     *
     * @param name        商品名
     * @param description 説明
     * @return 本文
     */
    private String nameAndDescriptionJson(String name, String description) {
        return """
                {"name": "%s", "description": "%s", "taxCategoryId": "%s", "priceExclTax": %d,
                 "categoryIds": [], "version": 0}""".formatted(name, description, standard, PRICE_EXCL_TAX);
    }

    /**
     * バージョン 0 の商品を、商品名・説明・税抜価格・所属カテゴリを変えて編集する。
     *
     * <p>税区分は標準のまま、所属カテゴリは衣類にする。
     *
     * @param id 商品 ID
     * @return 応答
     */
    private ResultActions updateProduct(UUID id) throws Exception {
        return products.update(id, """
                {"name": "%s", "description": "%s", "taxCategoryId": "%s", "priceExclTax": %d,
                 "categoryIds": ["%s"], "version": 0}"""
                .formatted(NEW_NAME, NEW_DESCRIPTION, standard, NEW_PRICE_EXCL_TAX, clothing));
    }

    /**
     * 衣類に所属する商品を登録する。
     *
     * @return 商品 ID
     */
    private UUID productInClothing() throws Exception {
        return products.createdId("""
                {"productCode": "%s", "name": "%s", "description": "", "taxCategoryId": "%s",
                 "priceExclTax": %d, "categoryIds": ["%s"]}""".formatted(PRODUCT_CODE, NAME, standard, PRICE_EXCL_TAX, clothing));
    }
}
