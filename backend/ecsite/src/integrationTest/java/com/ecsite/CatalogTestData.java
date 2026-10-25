package com.ecsite;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 統合テストで、税区分・カテゴリ・商品を B 側の API で作る補助（spec 002 のカート・注文のテストで使う）。<br />
 * 公開するごとに時計を 1 分進める（新着順が決まるように）。品番は呼ぶたびに振り直す。
 */
public class CatalogTestData {

    /**
     * PNG（登録のときに imgproxy で変換するので、本物の画像）
     */
    private static final byte[] PNG = TestImages.png();

    /**
     * 品番の連番（テストの中で重ならないように、乱数から始める）
     */
    private int sequence = (int) (Math.random() * 1_000_000);

    /**
     * MockMvc
     */
    private final MockMvc mockMvc;

    /**
     * テスト用の時計
     */
    private final MutableClock clock;

    /**
     * コンストラクタ
     *
     * @param mockMvc MockMvc
     * @param clock   テスト用の時計
     */
    public CatalogTestData(MockMvc mockMvc, MutableClock clock) {
        this.mockMvc = mockMvc;
        this.clock = clock;
    }

    /**
     * 開始・終了とも空白の税率を 1 つ持つ税区分を作る。
     *
     * @param name        名称
     * @param ratePercent 率（%）
     * @return 税区分 ID
     */
    public UUID taxCategory(String name, String ratePercent) throws Exception {
        var id = createdId(post("/api/admin/tax-categories").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "%s"}""".formatted(name)));
        mockMvc.perform(put("/api/admin/tax-categories/" + id + "/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "rates": [{"ratePercent": %s}]}""".formatted(ratePercent)))
                .andExpect(status().isNoContent());
        return id;
    }

    /**
     * カテゴリを登録する。
     *
     * @param name     名称
     * @param parentId 親のカテゴリ ID（最上位なら null）
     * @return カテゴリ ID
     */
    public UUID category(String name, UUID parentId) throws Exception {
        var parent = parentId == null ? "null" : "\"%s\"".formatted(parentId);
        return createdId(post("/api/admin/categories").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "%s", "parentId": %s}""".formatted(name, parent)));
    }

    /**
     * 軸を指定して商品を登録する。軸がなければ、バリアントを 1 つ持つ。品番は呼ぶたびに振り直す。
     *
     * @param taxCategoryId 税区分 ID
     * @param name          商品名
     * @param priceExclTax  税抜価格
     * @param axes          軸の名前
     * @return 商品
     */
    public TestProduct product(UUID taxCategoryId, String name, long priceExclTax, String... axes) throws Exception {
        sequence++;
        return product("TD-" + sequence, taxCategoryId, name, priceExclTax, axes);
    }

    /**
     * 品番と軸を指定して商品を登録する。軸がなければ、バリアントを 1 つ持つ。
     *
     * @param productCode   商品品番
     * @param taxCategoryId 税区分 ID
     * @param name          商品名
     * @param priceExclTax  税抜価格
     * @param axes          軸の名前
     * @return 商品
     */
    public TestProduct product(String productCode, UUID taxCategoryId, String name, long priceExclTax, String... axes)
            throws Exception {
        var axesJson = Arrays.stream(axes).map("\"%s\""::formatted).collect(Collectors.joining(", "));
        var id = createdId(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"productCode": "%s", "name": "%s", "description": "説明", "taxCategoryId": "%s",
                 "priceExclTax": %d, "axes": [%s]}""".formatted(productCode, name, taxCategoryId, priceExclTax, axesJson)));
        var product = new TestProduct(id, productCode);
        if (axes.length == 0) {
            product.variant();
        }
        return product;
    }

    /**
     * 日時を JSON の値にする（null は null）
     */
    private static String jsonInstant(Instant instant) {
        return instant == null ? "null" : "\"" + instant + "\"";
    }

    /**
     * 作成の要求を送り、201 を確認して、本文の ID を返す
     */
    private UUID createdId(MockHttpServletRequestBuilder request) throws Exception {
        var body = mockMvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return TestJson.createdId(body);
    }

    /**
     * テストで作った商品。バリアントの登録・画像・状態の切り替えを続けて書けるようにする
     */
    public class TestProduct {

        /**
         * 商品 ID
         */
        public final UUID id;

        /**
         * 商品品番
         */
        public final String productCode;

        /**
         * バリアント ID（登録順）
         */
        public final List<UUID> variantIds = new ArrayList<>();

        /**
         * バリアント品番（登録順）
         */
        public final List<String> variantCodes = new ArrayList<>();

        /**
         * 画像 ID（表示順）
         */
        public final List<UUID> imageIds = new ArrayList<>();

        /**
         * 画像の URL（表示順）
         */
        public final List<String> imageUrls = new ArrayList<>();

        /**
         * 商品のバージョン
         */
        private long version;

        TestProduct(UUID id, String productCode) {
            this.id = id;
            this.productCode = productCode;
        }

        /**
         * 軸の値を指定してバリアントを登録する。
         */
        public TestProduct variant(String... axisValues) throws Exception {
            var values = Arrays.stream(axisValues).map("\"%s\""::formatted).collect(Collectors.joining(", "));
            var code = "%s-%d".formatted(productCode, variantIds.size() + 1);
            variantIds.add(createdId(post("/api/admin/products/" + id + "/variants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"variantCode": "%s", "axisValues": [%s], "version": %d}""".formatted(code, values, version))));
            variantCodes.add(code);
            version++;
            return this;
        }

        /**
         * 画像のアップロードを受け付け、署名付き URL へ PNG を PUT する。
         *
         * @return 受付の ID
         */
        public String upload() throws Exception {
            var body = mockMvc.perform(post("/api/admin/products/" + id + "/image-uploads")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"contentType": "image/png", "contentLength": %d}""".formatted(PNG.length)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            var request = HttpRequest.newBuilder(URI.create(JsonPath.read(body, "$.uploadUrl")))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(PNG));
            JsonPath.<Map<String, String>>read(body, "$.headers").forEach(request::header);
            try (var client = HttpClient.newHttpClient()) {
                assertThat(client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
            }
            return JsonPath.read(body, "$.uploadId");
        }

        /**
         * 受け付けた画像を確定する。結果は呼び出し側で確認する（商品のバージョンは進めない）。
         *
         * @param uploadId 受付の ID
         * @param version  送るバージョン
         * @return 応答
         */
        public ResultActions addImage(String uploadId, long version) throws Exception {
            return mockMvc.perform(post("/api/admin/products/" + id + "/images")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"uploadId": "%s", "version": %d}""".formatted(uploadId, version)));
        }

        /**
         * 画像を 1 枚登録する（受付 → ストレージへの PUT → 確定）。
         */
        public TestProduct image() throws Exception {
            addImage(upload(), version).andExpect(status().isCreated());
            version++;
            var detail = mockMvc.perform(get("/api/admin/products/" + id)).andReturn().getResponse().getContentAsString();
            imageUrls.clear();
            imageUrls.addAll(JsonPath.read(detail, "$.images[*].url"));
            imageIds.clear();
            JsonPath.<List<String>>read(detail, "$.images[*].id").forEach(imageId -> imageIds.add(UUID.fromString(imageId)));
            return this;
        }

        /**
         * バリアントに画像を指定する。
         */
        public TestProduct variantImage(int variantIndex, int imageIndex) throws Exception {
            mockMvc.perform(put("/api/admin/products/" + id + "/variants/" + variantIds.get(variantIndex) + "/image")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"imageId": "%s", "version": %d}""".formatted(imageIds.get(imageIndex), version)))
                    .andExpect(status().isNoContent());
            version++;
            return this;
        }

        /**
         * 公開し、時計を 1 分進める。
         */
        public TestProduct publish() throws Exception {
            mockMvc.perform(put("/api/admin/products/" + id + "/publish")).andExpect(status().isNoContent());
            clock.setInstant(clock.instant().plus(Duration.ofMinutes(1)));
            return this;
        }

        /**
         * 非公開にする。
         */
        public TestProduct unpublish() throws Exception {
            mockMvc.perform(put("/api/admin/products/" + id + "/unpublish")).andExpect(status().isNoContent());
            return this;
        }

        /**
         * 販売を終了する。
         */
        public TestProduct discontinue() throws Exception {
            mockMvc.perform(put("/api/admin/products/" + id + "/discontinue")).andExpect(status().isNoContent());
            return this;
        }

        /**
         * 公開期間を設定する（null は制限なし）。
         */
        public TestProduct period(Instant publishStart, Instant publishEnd) throws Exception {
            var detail = mockMvc.perform(get("/api/admin/products/" + id)).andReturn().getResponse().getContentAsString();
            var current = JsonPath.<Integer>read(detail, "$.version");
            mockMvc.perform(put("/api/admin/products/" + id + "/publication-period")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"publishStart": %s, "publishEnd": %s, "version": %d}"""
                                    .formatted(jsonInstant(publishStart), jsonInstant(publishEnd), current)))
                    .andExpect(status().isNoContent());
            version = current + 1L;
            return this;
        }

        /**
         * バリアントを販売停止にする。
         */
        public TestProduct suspend(int variantIndex) throws Exception {
            mockMvc.perform(put("/api/admin/products/" + id + "/variants/" + variantIds.get(variantIndex) + "/suspend"))
                    .andExpect(status().isNoContent());
            return this;
        }

        /**
         * 税抜価格を変える。
         */
        public TestProduct changePrice(UUID taxCategoryId, long priceExclTax) throws Exception {
            var detail = mockMvc.perform(get("/api/admin/products/" + id)).andReturn().getResponse().getContentAsString();
            mockMvc.perform(put("/api/admin/products/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "%s", "description": "説明", "taxCategoryId": "%s", "priceExclTax": %d,
                                     "categoryIds": [], "version": %d}"""
                                    .formatted(JsonPath.<String>read(detail, "$.name"), taxCategoryId, priceExclTax,
                                            JsonPath.<Integer>read(detail, "$.version"))))
                    .andExpect(status().isNoContent());
            version = JsonPath.<Integer>read(detail, "$.version") + 1L;
            return this;
        }
    }
}
