package com.ecsite.module.catalog.presentation;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.contains;
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
 * 商品 API の受け入れテスト（T309、US-3〜5）。<br />
 * 現在時刻は {@link MutableClock} で 2026-10-08 00:00（日本時間）に固定する。
 * 税区分（標準 10%、軽減 8%、有効な税率のない税区分）は、テストごとに税区分 API で作る。テストごとにロールバックする。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class ProductApiTest {

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/products";

    /**
     * 存在しない ID
     */
    private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

    /**
     * 現在：2026-10-08 00:00（日本時間）
     */
    private static final Instant NOW = OffsetDateTime.parse("2026-10-08T00:00:00+09:00").toInstant();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    /**
     * 標準（10%）
     */
    private String standard;

    /**
     * 軽減（8%）
     */
    private String reduced;

    /**
     * 有効な税率がない（2027-04-01 から 10%）
     */
    private String notYetEffective;

    private String clothing;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        standard = createTaxCategory("標準", """
                [{"ratePercent": 10}]""");
        reduced = createTaxCategory("軽減", """
                [{"ratePercent": 8}]""");
        notYetEffective = createTaxCategory("将来", """
                [{"ratePercent": 10, "effectiveFrom": "2027-04-01T00:00:00+09:00"}]""");
        clothing = createdId(post("/api/admin/categories").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "衣類"}"""));
    }

    @Nested
    @DisplayName("AC-6、13、14、15、16、17、25 商品の登録")
    class Create {

        @Test
        @DisplayName("AC-13、17 指定した内容で登録され、非公開になる。Location の URL で詳細を取得できる")
        void createsAndGetsByLocation() throws Exception {
            var result = createProduct("""
                    {"productCode": "ABC-001", "name": "T シャツ", "description": "綿 100%%",
                     "taxCategoryId": "%s", "priceExclTax": 1000,
                     "categoryIds": ["%s"], "axes": ["色", "サイズ"]}""".formatted(standard, clothing))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern(".*" + BASE + "/[0-9a-f-]{36}")))
                    .andReturn();
            String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

            mockMvc.perform(get(result.getResponse().getHeader(HttpHeaders.LOCATION)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.productCode").value("ABC-001"))
                    .andExpect(jsonPath("$.name").value("T シャツ"))
                    .andExpect(jsonPath("$.description").value("綿 100%"))
                    .andExpect(jsonPath("$.taxCategoryId").value(standard))
                    .andExpect(jsonPath("$.priceExclTax").value(1000))
                    .andExpect(jsonPath("$.priceInclTax").value(1100))
                    .andExpect(jsonPath("$.categoryIds", contains(clothing)))
                    .andExpect(jsonPath("$.axes", contains("色", "サイズ")))
                    .andExpect(jsonPath("$.status").value("UNPUBLISHED"))
                    .andExpect(jsonPath("$.firstShownAt").value(nullValue()))
                    .andExpect(jsonPath("$.variants").isEmpty())
                    .andExpect(jsonPath("$.version").value(0))
                    .andExpect(jsonPath("$.createdAt").isString())
                    .andExpect(jsonPath("$.updatedAt").isString());
        }

        @Test
        @DisplayName("AC-13 所属カテゴリと軸は省略でき、空になる")
        void createsWithoutCategoriesAndAxes() throws Exception {
            var id = productId("ABC-001");

            getProduct(id)
                    .andExpect(jsonPath("$.categoryIds").isEmpty())
                    .andExpect(jsonPath("$.axes").isEmpty());
        }

        @Test
        @DisplayName("AC-14 商品品番が重複すれば 409")
        void rejectsDuplicatedProductCode() throws Exception {
            productId("ABC-001");

            createProduct(productJson("ABC-001", standard, 1000))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("PRODUCT_CODE_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("AC-15 商品品番の形式が違えば 400（英小文字、先頭・末尾・連続のハイフン）")
        void rejectsInvalidProductCode() throws Exception {
            for (var code : new String[]{"abc-001", "-ABC", "ABC-", "ABC--1"}) {
                createProduct(productJson(code, standard, 1000))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
            }
        }

        @Test
        @DisplayName("AC-16 税区分がなければ 400")
        void rejectsMissingTaxCategory() throws Exception {
            createProduct("""
                    {"productCode": "ABC-001", "name": "T シャツ", "description": "", "priceExclTax": 1000}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-6 現時点で有効な税率のない税区分は 400。存在しない税区分も 400")
        void rejectsTaxCategoryWithoutCurrentRate() throws Exception {
            createProduct(productJson("ABC-001", notYetEffective, 1000))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_CATEGORY_NOT_ASSIGNABLE"));
            createProduct(productJson("ABC-001", UNKNOWN_ID, 1000))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_CATEGORY_NOT_ASSIGNABLE"));
        }

        @Test
        @DisplayName("AC-25 税抜価格が 1 円未満なら 400")
        void rejectsPriceLessThanOneYen() throws Exception {
            createProduct(productJson("ABC-001", standard, 0))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-13 軸が 4 つ以上なら 400")
        void rejectsFourAxes() throws Exception {
            createProduct("""
                    {"productCode": "ABC-001", "name": "T シャツ", "description": "", "taxCategoryId": "%s",
                     "priceExclTax": 1000, "axes": ["色", "サイズ", "素材", "柄"]}""".formatted(standard))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("存在しないカテゴリを指定すれば 400")
        void rejectsUnknownCategory() throws Exception {
            createProduct("""
                    {"productCode": "ABC-001", "name": "T シャツ", "description": "", "taxCategoryId": "%s",
                     "priceExclTax": 1000, "categoryIds": ["%s"]}""".formatted(standard, UNKNOWN_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CATEGORY_ID"));
        }
    }

    @Nested
    @DisplayName("取得")
    class Get {

        @Test
        @DisplayName("存在しない ID は 404")
        void returnsNotFound() throws Exception {
            getProduct(UNKNOWN_ID)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("AC-6、18、19、25、26 商品の編集")
    class Update {

        @Test
        @DisplayName("AC-18、26 商品名、説明、税区分、税抜価格、所属カテゴリを変更でき、バージョンが上がる。価格の変更は税込価格に反映される")
        void updates() throws Exception {
            var id = productId("ABC-001");

            updateProduct(id, """
                    {"name": "ポロシャツ", "description": "綿 50%%", "taxCategoryId": "%s", "priceExclTax": 2000,
                     "categoryIds": ["%s"], "version": 0}""".formatted(standard, clothing))
                    .andExpect(status().isNoContent());

            getProduct(id)
                    .andExpect(jsonPath("$.productCode").value("ABC-001"))
                    .andExpect(jsonPath("$.name").value("ポロシャツ"))
                    .andExpect(jsonPath("$.description").value("綿 50%"))
                    .andExpect(jsonPath("$.priceExclTax").value(2000))
                    .andExpect(jsonPath("$.priceInclTax").value(2200))
                    .andExpect(jsonPath("$.categoryIds", contains(clothing)))
                    .andExpect(jsonPath("$.version").value(1));
        }

        @Test
        @DisplayName("AC-19 税区分を変えると、税込価格が変更後の税率で計算される")
        void recalculatesPriceInclTaxWithNewTaxCategory() throws Exception {
            var id = productId("ABC-001");
            getProduct(id).andExpect(jsonPath("$.priceInclTax").value(1100));

            updateProduct(id, updateJson(reduced, 1000, 0)).andExpect(status().isNoContent());

            getProduct(id).andExpect(jsonPath("$.priceInclTax").value(1080));
        }

        @Test
        @DisplayName("AC-6 現時点で有効な税率のない税区分に変えると 400")
        void rejectsTaxCategoryWithoutCurrentRate() throws Exception {
            var id = productId("ABC-001");

            updateProduct(id, updateJson(notYetEffective, 1000, 0))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TAX_CATEGORY_NOT_ASSIGNABLE"));
        }

        @Test
        @DisplayName("AC-25 税抜価格が 1 円未満なら 400")
        void rejectsPriceLessThanOneYen() throws Exception {
            var id = productId("ABC-001");

            updateProduct(id, updateJson(standard, 0, 0))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("取得時のバージョンと一致しなければ 409")
        void rejectsVersionMismatch() throws Exception {
            var id = productId("ABC-001");
            updateProduct(id, updateJson(standard, 2000, 0)).andExpect(status().isNoContent());

            updateProduct(id, updateJson(standard, 3000, 0))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("存在しない商品は 404")
        void rejectsUnknownProduct() throws Exception {
            updateProduct(UNKNOWN_ID, updateJson(standard, 1000, 0))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("AC-20、21、22、23、24、27、71 バリアント")
    class Variants {

        @Test
        @DisplayName("AC-20 品番と軸の値で登録でき、本文で ID が返る（Location は返さない）")
        void addsVariant() throws Exception {
            var id = productId("ABC-001", "色", "サイズ");

            var result = addVariant(id, "ABC-001-WH-M", 0, "白", "M")
                    .andExpect(status().isCreated())
                    .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                    .andReturn();
            String variantId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

            getProduct(id)
                    .andExpect(jsonPath("$.variants[0].id").value(variantId))
                    .andExpect(jsonPath("$.variants[0].variantCode").value("ABC-001-WH-M"))
                    .andExpect(jsonPath("$.variants[0].axisValues", contains("白", "M")))
                    .andExpect(jsonPath("$.variants[0].salesSuspended").value(false))
                    .andExpect(jsonPath("$.version").value(1));
        }

        @Test
        @DisplayName("AC-21 バリアント品番が別の商品と重複すれば 409")
        void rejectsDuplicatedVariantCode() throws Exception {
            var first = productId("ABC-001");
            addVariant(first, "V1", 0).andExpect(status().isCreated());
            var second = productId("ABC-002");

            addVariant(second, "V1", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("VARIANT_CODE_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("AC-21 バリアント品番の形式が違えば 400")
        void rejectsInvalidVariantCode() throws Exception {
            var id = productId("ABC-001");

            addVariant(id, "v-1", 0)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-22 すべての軸の値を持たなければ 400")
        void rejectsMissingAxisValue() throws Exception {
            var id = productId("ABC-001", "色", "サイズ");

            addVariant(id, "V1", 0, "白")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_AXIS_VALUES"));
        }

        @Test
        @DisplayName("AC-23 軸の値の組み合わせが重複すれば 409")
        void rejectsDuplicatedAxisValues() throws Exception {
            var id = productId("ABC-001", "色", "サイズ");
            addVariant(id, "V1", 0, "白", "M").andExpect(status().isCreated());

            addVariant(id, "V2", 1, "白", "M")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("VARIANT_AXIS_VALUES_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("AC-24 軸のない商品のバリアントは 1 つだけ。2 つ目は 400")
        void rejectsSecondVariantWithoutAxes() throws Exception {
            var id = productId("ABC-001");
            addVariant(id, "V1", 0).andExpect(status().isCreated());

            addVariant(id, "V2", 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("SINGLE_VARIANT_ONLY"));
        }

        @Test
        @DisplayName("AC-27 表示順を変更できる。過不足があれば 400")
        void reordersVariants() throws Exception {
            var id = productId("ABC-001", "色");
            var white = variantId(id, "V-WH", 0, "白");
            var black = variantId(id, "V-BK", 1, "黒");

            reorderVariants(id, 2, black, white).andExpect(status().isNoContent());
            getProduct(id)
                    .andExpect(jsonPath("$.variants[*].id", contains(black, white)))
                    .andExpect(jsonPath("$.version").value(3));

            reorderVariants(id, 3, black)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_VARIANT_ORDER"));
        }

        @Test
        @DisplayName("AC-71 C 側に表示されていない商品のバリアントは削除できる")
        void deletesVariantOfNeverShownProduct() throws Exception {
            var id = productId("ABC-001", "色");
            var white = variantId(id, "V-WH", 0, "白");

            deleteVariant(id, white, 1).andExpect(status().isNoContent());

            getProduct(id).andExpect(jsonPath("$.variants").isEmpty());
        }

        @Test
        @DisplayName("AC-71 表示されたことのある商品のバリアントは削除できず 400")
        void rejectsDeletingVariantOfShownProduct() throws Exception {
            var id = productId("ABC-001", "色");
            var white = variantId(id, "V-WH", 0, "白");
            variantId(id, "V-BK", 1, "黒");
            publish(id).andExpect(status().isNoContent());
            unpublish(id).andExpect(status().isNoContent());

            deleteVariant(id, white, 4)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PRODUCT_ALREADY_SHOWN"));
        }

        @Test
        @DisplayName("存在しないバリアントの削除は 404")
        void rejectsUnknownVariant() throws Exception {
            var id = productId("ABC-001");

            deleteVariant(id, UNKNOWN_ID, 0)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("登録・削除・並び替えで、バージョンが一致しなければ 409")
        void rejectsVersionMismatch() throws Exception {
            var id = productId("ABC-001", "色");
            var white = variantId(id, "V-WH", 0, "白");

            addVariant(id, "V-BK", 0, "黒")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
            reorderVariants(id, 0, white)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
            deleteVariant(id, white, 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("存在しない商品へのバリアントの登録は 404")
        void rejectsUnknownProduct() throws Exception {
            addVariant(UNKNOWN_ID, "V1", 0)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("AC-29、30、31、32、33 公開・非公開・販売終了・削除")
    class Status {

        @Test
        @DisplayName("AC-29 公開でき、初回表示日時が記録される。同じ状態への切り替えは 204 で、何も変わらない")
        void publishes() throws Exception {
            var id = productWithVariant("ABC-001");

            publish(id).andExpect(status().isNoContent());
            getProduct(id)
                    .andExpect(jsonPath("$.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.firstShownAt").value("2026-10-07T15:00:00Z"))
                    .andExpect(jsonPath("$.version").value(2));

            publish(id).andExpect(status().isNoContent());
            getProduct(id).andExpect(jsonPath("$.version").value(2));
        }

        @Test
        @DisplayName("AC-31 バリアントのない商品は公開できず 400")
        void rejectsPublishingWithoutVariants() throws Exception {
            var id = productId("ABC-001");

            publish(id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PRODUCT_HAS_NO_VARIANTS"));
        }

        @Test
        @DisplayName("AC-30 非公開にできる。すでに非公開なら 204")
        void unpublishes() throws Exception {
            var id = productWithVariant("ABC-001");
            unpublish(id).andExpect(status().isNoContent());
            publish(id).andExpect(status().isNoContent());

            unpublish(id).andExpect(status().isNoContent());

            getProduct(id).andExpect(jsonPath("$.status").value("UNPUBLISHED"));
        }

        @Test
        @DisplayName("AC-32 販売終了にでき、販売終了からは公開にも非公開にも戻せず 400。すでに販売終了なら 204")
        void discontinues() throws Exception {
            var id = productWithVariant("ABC-001");
            publish(id).andExpect(status().isNoContent());

            discontinue(id).andExpect(status().isNoContent());
            discontinue(id).andExpect(status().isNoContent());

            getProduct(id).andExpect(jsonPath("$.status").value("DISCONTINUED"));
            publish(id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PRODUCT_DISCONTINUED"));
            unpublish(id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PRODUCT_DISCONTINUED"));
        }

        @Test
        @DisplayName("存在しない商品の状態の切り替えは 404")
        void rejectsUnknownProduct() throws Exception {
            publish(UNKNOWN_ID)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("AC-33 C 側に一度も表示されていない商品は削除できる")
        void deletesNeverShownProduct() throws Exception {
            var id = productWithVariant("ABC-001");

            deleteProduct(id, 1).andExpect(status().isNoContent());

            getProduct(id).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-33 表示されたことのある商品は削除できず 400")
        void rejectsDeletingShownProduct() throws Exception {
            var id = productWithVariant("ABC-001");
            publish(id).andExpect(status().isNoContent());
            discontinue(id).andExpect(status().isNoContent());

            deleteProduct(id, 3)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PRODUCT_ALREADY_SHOWN"));
        }

        @Test
        @DisplayName("商品の削除で、バージョンが一致しなければ 409")
        void rejectsDeletingWithVersionMismatch() throws Exception {
            var id = productWithVariant("ABC-001");

            deleteProduct(id, 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }
    }

    @Nested
    @DisplayName("AC-34、35、36 公開期間")
    class PublicationPeriod {

        @Test
        @DisplayName("AC-34 公開開始・終了日時を設定でき、詳細に出て、バージョンが上がる。空にもできる")
        void setsPeriod() throws Exception {
            var id = productWithVariant("ABC-001");

            setPeriod(id, "\"2026-10-10T00:00:00+09:00\"", "\"2026-10-20T00:00:00+09:00\"", 1)
                    .andExpect(status().isNoContent());
            getProduct(id)
                    .andExpect(jsonPath("$.publishStart").value("2026-10-09T15:00:00Z"))
                    .andExpect(jsonPath("$.publishEnd").value("2026-10-19T15:00:00Z"))
                    .andExpect(jsonPath("$.version").value(2));

            setPeriod(id, "null", "null", 2).andExpect(status().isNoContent());
            getProduct(id)
                    .andExpect(jsonPath("$.publishStart").doesNotExist())
                    .andExpect(jsonPath("$.publishEnd").doesNotExist());
        }

        @Test
        @DisplayName("公開中に開始日時を未来にすると、初回表示日時がその日時になり、C 側に表示されていない扱いになる")
        void reschedulesFirstShownAt() throws Exception {
            var id = productWithVariant("ABC-001");
            setPeriod(id, "\"2026-10-10T00:00:00+09:00\"", "null", 1).andExpect(status().isNoContent());

            publish(id).andExpect(status().isNoContent());

            getProduct(id).andExpect(jsonPath("$.firstShownAt").value("2026-10-09T15:00:00Z"));
            // 表示前なので、予約を取り消せば削除できる（AC-33）
            unpublish(id).andExpect(status().isNoContent());
            deleteProduct(id, 4).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("AC-36 終了日時が開始日時より後でなければ 400")
        void rejectsEndNotAfterStart() throws Exception {
            var id = productWithVariant("ABC-001");

            setPeriod(id, "\"2026-10-10T00:00:00+09:00\"", "\"2026-10-10T00:00:00+09:00\"", 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PUBLICATION_PERIOD"));
        }

        @Test
        @DisplayName("取得時のバージョンと一致しなければ 409。存在しない商品は 404")
        void rejectsStaleVersionAndUnknownProduct() throws Exception {
            var id = productWithVariant("ABC-001");

            setPeriod(id, "null", "null", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
            setPeriod(UNKNOWN_ID, "null", "null", 0).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("AC-28 バリアントの販売停止")
    class SalesSuspension {

        @Test
        @DisplayName("AC-28 販売停止・解除でき、バージョンが上がる。同じ状態への切り替えは 204 で、何も変わらない")
        void suspendsAndResumes() throws Exception {
            var id = productWithVariant("ABC-001");
            var variantId = JsonPath.<String>read(getProduct(id).andReturn().getResponse().getContentAsString(),
                    "$.variants[0].id");

            suspend(id, variantId).andExpect(status().isNoContent());
            getProduct(id)
                    .andExpect(jsonPath("$.variants[0].salesSuspended").value(true))
                    .andExpect(jsonPath("$.version").value(2));

            suspend(id, variantId).andExpect(status().isNoContent());
            getProduct(id).andExpect(jsonPath("$.version").value(2));

            resume(id, variantId).andExpect(status().isNoContent());
            getProduct(id)
                    .andExpect(jsonPath("$.variants[0].salesSuspended").value(false))
                    .andExpect(jsonPath("$.version").value(3));
        }

        @Test
        @DisplayName("存在しない商品・バリアントは 404")
        void rejectsUnknown() throws Exception {
            var id = productWithVariant("ABC-001");

            suspend(UNKNOWN_ID, UNKNOWN_ID).andExpect(status().isNotFound());
            suspend(id, UNKNOWN_ID)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
            resume(id, UNKNOWN_ID).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("AC-12 商品が所属しているカテゴリ")
    class AssignedCategory {

        @Test
        @DisplayName("AC-12 商品が所属しているカテゴリは削除できず 400。所属を外せば削除できる")
        void rejectsDeletingCategoryWithProducts() throws Exception {
            var id = createdId(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                    {"productCode": "ABC-001", "name": "T シャツ", "description": "", "taxCategoryId": "%s",
                     "priceExclTax": 1000, "categoryIds": ["%s"]}""".formatted(standard, clothing)));

            mockMvc.perform(delete("/api/admin/categories/" + clothing))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("CATEGORY_HAS_PRODUCTS"));

            updateProduct(id, updateJson(standard, 1000, 0)).andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/admin/categories/" + clothing)).andExpect(status().isNoContent());
        }
    }

    // ============================== 補助 ==============================
    // 商品とバリアントの登録は、このテストが確かめる API なので、CatalogTestData を使わず、品番・本文・応答をここで扱う

    /**
     * 税区分を登録し、税率の一覧（JSON）を設定して、その ID を返す
     */
    private String createTaxCategory(String name, String rates) throws Exception {
        var id = createdId(post("/api/admin/tax-categories").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "%s"}""".formatted(name)));
        mockMvc.perform(put("/api/admin/tax-categories/" + id + "/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "rates": %s}""".formatted(rates)))
                .andExpect(status().isNoContent());
        return id;
    }

    /**
     * 要求を送って 201 を確かめ、応答の本文の ID を返す
     */
    private String createdId(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        var body = mockMvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /**
     * 商品の登録の本文（軸なし、所属カテゴリなし）
     */
    private String productJson(String code, String taxCategoryId, long price) {
        return """
                {"productCode": "%s", "name": "T シャツ", "description": "", "taxCategoryId": "%s", "priceExclTax": %d}"""
                .formatted(code, taxCategoryId, price);
    }

    /**
     * 商品の編集の本文（所属カテゴリなし）
     */
    private String updateJson(String taxCategoryId, long price, long version) {
        return """
                {"name": "T シャツ", "description": "", "taxCategoryId": "%s", "priceExclTax": %d,
                 "categoryIds": [], "version": %d}""".formatted(taxCategoryId, price, version);
    }

    /**
     * 商品を登録する
     */
    private ResultActions createProduct(String json) throws Exception {
        return mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /**
     * 標準の税区分で商品を登録して、その ID を返す
     */
    private String productId(String code, String... axes) throws Exception {
        var axesJson = Arrays.stream(axes).map("\"%s\""::formatted).collect(Collectors.joining(", "));
        return createdId(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                {"productCode": "%s", "name": "T シャツ", "description": "", "taxCategoryId": "%s",
                 "priceExclTax": 1000, "axes": [%s]}""".formatted(code, standard, axesJson)));
    }

    /**
     * 軸がなく、バリアントを 1 つ持つ非公開の商品（バージョン 1）
     */
    private String productWithVariant(String code) throws Exception {
        var id = productId(code);
        addVariant(id, code + "-V", 0).andExpect(status().isCreated());
        return id;
    }

    /**
     * 商品の詳細を取得する
     */
    private ResultActions getProduct(String id) throws Exception {
        return mockMvc.perform(get(BASE + "/" + id));
    }

    /**
     * 商品を編集する
     */
    private ResultActions updateProduct(String id, String json) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /**
     * 商品を削除する
     */
    private ResultActions deleteProduct(String id, long version) throws Exception {
        return mockMvc.perform(delete(BASE + "/" + id).param("version", String.valueOf(version)));
    }

    /**
     * バリアントを登録する
     */
    private ResultActions addVariant(String productId, String code, long version, String... axisValues) throws Exception {
        var values = Arrays.stream(axisValues).map("\"%s\""::formatted).collect(Collectors.joining(", "));
        return mockMvc.perform(post(BASE + "/" + productId + "/variants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"variantCode": "%s", "axisValues": [%s], "version": %d}""".formatted(code, values, version)));
    }

    /**
     * バリアントを登録して、その ID を返す
     */
    private String variantId(String productId, String code, long version, String... axisValues) throws Exception {
        var body = addVariant(productId, code, version, axisValues)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /**
     * バリアントを、指定した順に並び替える
     */
    private ResultActions reorderVariants(String productId, long version, String... variantIds) throws Exception {
        var ids = Arrays.stream(variantIds).map("\"%s\""::formatted).collect(Collectors.joining(", "));
        return mockMvc.perform(put(BASE + "/" + productId + "/variants/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"variantIds": [%s], "version": %d}""".formatted(ids, version)));
    }

    /**
     * バリアントを削除する
     */
    private ResultActions deleteVariant(String productId, String variantId, long version) throws Exception {
        return mockMvc.perform(delete(BASE + "/" + productId + "/variants/" + variantId)
                .param("version", String.valueOf(version)));
    }

    /**
     * 商品を公開する
     */
    private ResultActions publish(String id) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id + "/publish"));
    }

    /**
     * 商品を非公開にする
     */
    private ResultActions unpublish(String id) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id + "/unpublish"));
    }

    /**
     * 商品を販売終了にする
     */
    private ResultActions discontinue(String id) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id + "/discontinue"));
    }

    /**
     * 公開期間を設定する。日時は JSON の値（文字列か null）で渡す
     */
    private ResultActions setPeriod(String id, String publishStart, String publishEnd, long version) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id + "/publication-period")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"publishStart": %s, "publishEnd": %s, "version": %d}""".formatted(publishStart, publishEnd, version)));
    }

    /**
     * バリアントを販売停止にする
     */
    private ResultActions suspend(String productId, String variantId) throws Exception {
        return mockMvc.perform(put(BASE + "/" + productId + "/variants/" + variantId + "/suspend"));
    }

    /**
     * バリアントの販売停止を解除する
     */
    private ResultActions resume(String productId, String variantId) throws Exception {
        return mockMvc.perform(put(BASE + "/" + productId + "/variants/" + variantId + "/resume"));
    }
}
