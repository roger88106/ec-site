package com.ecsite.module.catalog.presentation;

import com.ecsite.support.http.TestJson;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理の商品 API を呼ぶ補助
 *
 * <p>{@link ProductApiTest}、{@link ProductVariantApiTest}、{@link ProductStatusApiTest} が共有する。
 * 商品とバリアントの登録は、これらのテストが確認する API なので、{@code CatalogTestData} でなく、ここで品番・本文・応答を扱う。
 * 税区分とカテゴリは {@code CatalogTestData} で作る。
 */
final class ProductRequests {

    /**
     * API のパス
     */
    static final String BASE = "/api/admin/products";

    /**
     * 補助の既定の商品名
     */
    static final String DEFAULT_NAME = "T シャツ";

    /**
     * 補助の既定の税抜価格（円）
     */
    static final long DEFAULT_PRICE_EXCL_TAX = 1000;

    /**
     * API を呼ぶ MockMvc
     */
    private final MockMvc mockMvc;

    /**
     * 補助で登録する商品の税区分の ID
     */
    private final String taxCategoryId;

    /**
     * 補助を作る。
     *
     * @param mockMvc       API を呼ぶ MockMvc
     * @param taxCategoryId 補助で登録する商品の税区分の ID（テストごとに作るので、引数で受け取る）
     */
    ProductRequests(MockMvc mockMvc, String taxCategoryId) {
        this.mockMvc = mockMvc;
        this.taxCategoryId = taxCategoryId;
    }

    // ============================== 商品 ==============================

    /**
     * 商品を登録する。
     *
     * @param json 登録の本文
     * @return 応答
     */
    ResultActions create(String json) throws Exception {
        return mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /**
     * 商品を登録して 201 を確認し、応答の本文の ID を返す。
     *
     * @param json 登録の本文
     * @return 商品 ID
     */
    UUID createdId(String json) throws Exception {
        var body = create(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return TestJson.createdId(body);
    }

    /**
     * 補助の税区分で、軸を指定して商品を登録し、その ID を返す。
     *
     * <p>商品名は {@link #DEFAULT_NAME}、税抜価格は {@link #DEFAULT_PRICE_EXCL_TAX}、所属カテゴリはなし。バージョンは 0。
     *
     * @param code 商品品番
     * @param axes 軸の名前（なければ空）
     * @return 商品 ID
     */
    UUID product(String code, String... axes) throws Exception {
        return createdId("""
                {"productCode": "%s", "name": "%s", "description": "", "taxCategoryId": "%s",
                 "priceExclTax": %d, "axes": [%s]}""".formatted(code, DEFAULT_NAME, taxCategoryId, DEFAULT_PRICE_EXCL_TAX,
                quoted(axes)));
    }

    /**
     * 軸がなく、バリアントを 1 つ持つ非公開の商品を登録し、その ID を返す。
     *
     * <p>バリアント品番は商品品番に {@code -V} を付けたもの。バージョンは 1。
     *
     * @param code 商品品番
     * @return 商品 ID
     */
    UUID productWithVariant(String code) throws Exception {
        var id = product(code);
        addVariant(id, code + "-V", 0).andExpect(status().isCreated());
        return id;
    }

    /**
     * 商品の詳細を取得する。
     *
     * @param id 商品 ID
     * @return 応答
     */
    ResultActions getProduct(UUID id) throws Exception {
        return mockMvc.perform(get(BASE + "/" + id));
    }

    /**
     * 商品を編集する。
     *
     * @param id   商品 ID
     * @param json 編集の本文
     * @return 応答
     */
    ResultActions update(UUID id, String json) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /**
     * 商品を削除する。
     *
     * @param id      商品 ID
     * @param version 送るバージョン
     * @return 応答
     */
    ResultActions deleteProduct(UUID id, long version) throws Exception {
        return mockMvc.perform(delete(BASE + "/" + id).param("version", String.valueOf(version)));
    }

    /**
     * 商品の登録の本文を作る（軸なし、所属カテゴリなし）。
     *
     * @param code          商品品番
     * @param taxCategoryId 税区分の ID
     * @param price         税抜価格（円）
     * @return 本文
     */
    static String productJson(String code, String taxCategoryId, long price) {
        return """
                {"productCode": "%s", "name": "%s", "description": "", "taxCategoryId": "%s", "priceExclTax": %d}"""
                .formatted(code, DEFAULT_NAME, taxCategoryId, price);
    }

    /**
     * 商品の編集の本文を作る（商品名は {@link #DEFAULT_NAME}、説明は空、所属カテゴリなし）。
     *
     * @param taxCategoryId 税区分の ID
     * @param price         税抜価格（円）
     * @param version       送るバージョン
     * @return 本文
     */
    static String updateJson(String taxCategoryId, long price, long version) {
        return """
                {"name": "%s", "description": "", "taxCategoryId": "%s", "priceExclTax": %d,
                 "categoryIds": [], "version": %d}""".formatted(DEFAULT_NAME, taxCategoryId, price, version);
    }

    // ============================== 状態と公開期間 ==============================

    /**
     * 商品を公開する。
     *
     * @param id 商品 ID
     * @return 応答
     */
    ResultActions publish(UUID id) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id + "/publish"));
    }

    /**
     * 商品を非公開にする。
     *
     * @param id 商品 ID
     * @return 応答
     */
    ResultActions unpublish(UUID id) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id + "/unpublish"));
    }

    /**
     * 商品を販売終了にする。
     *
     * @param id 商品 ID
     * @return 応答
     */
    ResultActions discontinue(UUID id) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id + "/discontinue"));
    }

    /**
     * 公開期間を設定する。
     *
     * @param id           商品 ID
     * @param publishStart 公開開始日時の JSON の値（文字列か {@code null}）
     * @param publishEnd   公開終了日時の JSON の値（文字列か {@code null}）
     * @param version      送るバージョン
     * @return 応答
     */
    ResultActions setPeriod(UUID id, String publishStart, String publishEnd, long version) throws Exception {
        return mockMvc.perform(put(BASE + "/" + id + "/publication-period")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"publishStart": %s, "publishEnd": %s, "version": %d}""".formatted(publishStart, publishEnd, version)));
    }

    // ============================== バリアント ==============================

    /**
     * バリアントを登録する。
     *
     * @param productId  商品 ID
     * @param code       バリアント品番
     * @param version    送るバージョン
     * @param axisValues 軸の値（軸の順）
     * @return 応答
     */
    ResultActions addVariant(UUID productId, String code, long version, String... axisValues) throws Exception {
        return mockMvc.perform(post(BASE + "/" + productId + "/variants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"variantCode": "%s", "axisValues": [%s], "version": %d}""".formatted(code, quoted(axisValues), version)));
    }

    /**
     * バリアントを登録して 201 を確認し、その ID を返す。
     *
     * @param productId  商品 ID
     * @param code       バリアント品番
     * @param version    送るバージョン
     * @param axisValues 軸の値（軸の順）
     * @return バリアント ID
     */
    UUID variantId(UUID productId, String code, long version, String... axisValues) throws Exception {
        var body = addVariant(productId, code, version, axisValues)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return TestJson.createdId(body);
    }

    /**
     * バリアントを、指定した順に並び替える。
     *
     * @param productId  商品 ID
     * @param version    送るバージョン
     * @param variantIds 並べたい順のバリアント ID
     * @return 応答
     */
    ResultActions reorderVariants(UUID productId, long version, UUID... variantIds) throws Exception {
        var ids = Arrays.stream(variantIds).map("\"%s\""::formatted).collect(Collectors.joining(", "));
        return mockMvc.perform(put(BASE + "/" + productId + "/variants/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"variantIds": [%s], "version": %d}""".formatted(ids, version)));
    }

    /**
     * バリアントを削除する。
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @param version   送るバージョン
     * @return 応答
     */
    ResultActions deleteVariant(UUID productId, UUID variantId, long version) throws Exception {
        return mockMvc.perform(delete(BASE + "/" + productId + "/variants/" + variantId)
                .param("version", String.valueOf(version)));
    }

    /**
     * バリアントを販売停止にする。
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @return 応答
     */
    ResultActions suspend(UUID productId, UUID variantId) throws Exception {
        return mockMvc.perform(put(BASE + "/" + productId + "/variants/" + variantId + "/suspend"));
    }

    /**
     * バリアントの販売停止を解除する。
     *
     * @param productId 商品 ID
     * @param variantId バリアント ID
     * @return 応答
     */
    ResultActions resume(UUID productId, UUID variantId) throws Exception {
        return mockMvc.perform(put(BASE + "/" + productId + "/variants/" + variantId + "/resume"));
    }

    // ============================== 本文の補助 ==============================

    /**
     * 文字列を JSON の文字列にして、カンマで区切ってつなぐ。
     *
     * @param values 値
     * @return JSON の配列の中身
     */
    private static String quoted(String... values) {
        return Arrays.stream(values).map("\"%s\""::formatted).collect(Collectors.joining(", "));
    }
}
