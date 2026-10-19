package com.ecsite.module.catalog.presentation;

import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.TestcontainersConfig;
import com.ecsite.TestImages;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.jooq.DSLContext;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.tax.Tables.TAX_CATEGORY;
import static com.ecsite.jooq.tax.Tables.TAX_RATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品画像 API の受け入れテスト（T1018、US-7。plan 001 第 7 節）。<br />
 * ファイルは、受付の応答の署名付き URL へ実際に PUT する（Testcontainers の SeaweedFS）。登録のときに imgproxy で WebP に変換するので、本物の画像を使う。
 * 配信のファイルの削除はコミット後のイベントで行うので、このテストはロールバックせず、テストの後に作ったデータを消す。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class ProductImageApiTest {

    /**
     * API のパス
     */
    private static final String BASE = "/api/admin/products";

    /**
     * 存在しない ID
     */
    private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

    /**
     * このテストで作る商品の品番の接頭辞
     */
    private static final String CODE_PREFIX = "IMG-";

    /**
     * テストの現在時刻
     */
    private static final Instant NOW = OffsetDateTime.parse("2026-10-08T00:00:00+09:00").toInstant();

    /**
     * 画像の大きさの上限（10MB。AC-39）
     */
    private static final int MAX_BYTES = 10 * 1024 * 1024;

    private static final byte[] PNG = TestImages.png();

    private static final byte[] JPEG = TestImages.jpeg();

    private static final byte[] WEBP = TestImages.of("source.webp");

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    S3Client s3;

    @Autowired
    DSLContext dsl;

    private String taxCategoryId;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        taxCategoryId = createdId(post("/api/admin/tax-categories").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "画像の標準 %s"}""".formatted(UUID.randomUUID())));
        mockMvc.perform(put("/api/admin/tax-categories/" + taxCategoryId + "/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "rates": [{"ratePercent": 10}]}"""))
                .andExpect(status().isNoContent());
    }

    /**
     * ロールバックしないので、作った商品と税区分を消す（全件を数えるほかのテストに影響しないように）。<br />
     * 商品は DB から直接消す（配信のファイルはテストのコンテナに残るが、ほかのテストは参照しない）。
     */
    @AfterEach
    void cleanUp() {
        dsl.deleteFrom(PRODUCT).where(PRODUCT.PRODUCT_CODE.startsWith(CODE_PREFIX)).execute();
        dsl.deleteFrom(TAX_RATE).where(TAX_RATE.TAX_CATEGORY_ID.eq(UUID.fromString(taxCategoryId))).execute();
        dsl.deleteFrom(TAX_CATEGORY).where(TAX_CATEGORY.ID.eq(UUID.fromString(taxCategoryId))).execute();
    }

    @Nested
    @DisplayName("AC-37、39 受付と登録")
    class Upload {

        @Test
        @DisplayName("AC-37 受付の署名付き URL へ PUT して登録すると、商品の画像の最後に加わり、配信の URL でだれでも取得できる")
        void uploadsAndRegisters() throws Exception {
            var productId = productWithVariant();

            var first = upload(productId, "image/png", PNG);
            addImage(productId, first, 1).andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNotEmpty());
            var second = upload(productId, "image/jpeg", JPEG);
            addImage(productId, second, 2).andExpect(status().isCreated());

            var detail = detail(productId);
            assertThat(JsonPath.<List<String>>read(detail, "$.images[*].url")).hasSize(2);
            assertThat(JsonPath.<Integer>read(detail, "$.version")).isEqualTo(3);
            var urls = JsonPath.<List<String>>read(detail, "$.images[*].url");
            // 配信するのは、変換した WebP（AC-68）
            var delivered = fetch(urls.getFirst());
            assertThat(delivered.statusCode()).isEqualTo(200);
            assertThat(new String(delivered.body(), 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
            assertThat(delivered.headers().firstValue("Content-Type")).contains("image/webp");
            assertThat(urls).allSatisfy(url -> assertThat(url).endsWith(".webp"));
        }

        @Test
        @DisplayName("AC-39 WebP も受け付ける")
        void acceptsWebp() throws Exception {
            var productId = productWithVariant();

            addImage(productId, upload(productId, "image/webp", WEBP), 1).andExpect(status().isCreated());

            assertThat(JsonPath.<List<String>>read(detail(productId), "$.images[*].url")).singleElement()
                    .satisfies(url -> assertThat(url).endsWith(".webp"));
        }

        @Test
        @DisplayName("受付は、PUT に付けるヘッダーと期限を返す")
        void returnsUploadInstructions() throws Exception {
            var productId = productWithVariant();

            createUpload(productId, "image/png", PNG.length)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.uploadId").isNotEmpty())
                    .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                    .andExpect(jsonPath("$.headers.Content-Type").value("image/png"))
                    .andExpect(jsonPath("$.expiresAt").isNotEmpty());
        }

        @Test
        @DisplayName("AC-39、67、272 受け付ける形式以外は、契約の形式の一覧で 400")
        void rejectsUnsupportedFormat() throws Exception {
            var productId = productWithVariant();

            createUpload(productId, "image/gif", 100)
                    .andExpect(status().isBadRequest())
                    // 列挙にない値は本文を読めない誤りになり、項目ごとの誤り（errors）は付かない
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-39、272 10MB を超えるファイルは契約の上限で受け付けず 400。0 バイトは業務の検証で 400")
        void rejectsTooLargeFile() throws Exception {
            var productId = productWithVariant();

            createUpload(productId, "image/png", MAX_BYTES).andExpect(status().isCreated());
            createUpload(productId, "image/png", MAX_BYTES + 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("contentLength"));
            createUpload(productId, "image/png", 0)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("IMAGE_TOO_LARGE"));
        }

        @Test
        @DisplayName("AC-39 受付のファイルが 10MB を超えていれば、登録のときに大きさを確かめて 400")
        void rejectsOversizedUploadOnRegister() throws Exception {
            var productId = productWithVariant();
            var body = createUpload(productId, "image/png", PNG.length)
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            var uploadId = JsonPath.<String>read(body, "$.uploadId");
            // 署名付き URL は大きさを含むので、ストレージが大きさを検証しない場合に備えた確認を、直接置いて確かめる
            s3.putObject(request -> request.bucket(TestcontainersConfig.INCOMING_BUCKET).key(productId + "/" + uploadId),
                    RequestBody.fromBytes(Arrays.copyOf(PNG, MAX_BYTES + 1)));

            addImage(productId, uploadId, 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("IMAGE_TOO_LARGE"));
        }

        @Test
        @DisplayName("形式を偽ったファイル（中身が画像でない）は、登録のときに先頭のバイトで確かめて 400")
        void rejectsDisguisedFile() throws Exception {
            var productId = productWithVariant();
            var uploadId = upload(productId, "image/png", "これは画像ではない".getBytes());

            addImage(productId, uploadId, 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_IMAGE_FILE"));
            assertThat(JsonPath.<List<String>>read(detail(productId), "$.images")).isEmpty();
        }

        @Test
        @DisplayName("PUT していない uploadId、別の商品で受け付けた uploadId は登録できず 400")
        void rejectsUnknownUpload() throws Exception {
            var productId = productWithVariant();
            var otherId = productWithVariant();
            var otherUpload = upload(otherId, "image/png", PNG);

            addImage(productId, UUID.randomUUID().toString(), 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("IMAGE_UPLOAD_NOT_FOUND"));
            addImage(productId, otherUpload, 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("IMAGE_UPLOAD_NOT_FOUND"));
        }

        @Test
        @DisplayName("登録で、取得時のバージョンと一致しなければ 409。存在しない商品は 404")
        void rejectsStaleVersionAndUnknownProduct() throws Exception {
            var productId = productWithVariant();
            var uploadId = upload(productId, "image/png", PNG);

            addImage(productId, uploadId, 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
            createUpload(UNKNOWN_ID, "image/png", 100).andExpect(status().isNotFound());
            addImage(UNKNOWN_ID, uploadId, 0).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("AC-40 枚数の上限")
    class Limit {

        @Test
        @DisplayName("AC-40 20 枚登録した商品は、受付も登録も 400")
        void rejectsTwentyFirstImage() throws Exception {
            var productId = productWithVariant();
            String pending = null;
            for (int i = 1; i <= 20; i++) {
                if (i == 20) {
                    // 20 枚目を登録する前に受け付けておいたものは、登録のときに確かめ直す
                    pending = upload(productId, "image/png", PNG);
                }
                addImage(productId, upload(productId, "image/png", PNG), i).andExpect(status().isCreated());
            }

            createUpload(productId, "image/png", PNG.length)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TOO_MANY_PRODUCT_IMAGES"));
            addImage(productId, pending, 21)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TOO_MANY_PRODUCT_IMAGES"));
        }
    }

    @Nested
    @DisplayName("AC-37、38 並び替え・削除・バリアントの画像")
    class Manage {

        @Test
        @DisplayName("AC-37 送った順に並べ替えられる。過不足があれば 400、バージョンが一致しなければ 409")
        void reorders() throws Exception {
            var productId = productWithVariant();
            var ids = registerImages(productId, 3);

            reorder(productId, 4, ids.get(2), ids.get(0), ids.get(1)).andExpect(status().isNoContent());
            mockMvc.perform(get(BASE + "/" + productId))
                    .andExpect(jsonPath("$.images[*].id", contains(ids.get(2), ids.get(0), ids.get(1))));

            reorder(productId, 5, ids.get(0), ids.get(1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_IMAGE_ORDER"));
            reorder(productId, 4, ids.get(0), ids.get(1), ids.get(2)).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("AC-38 バリアントにその商品の画像を指定でき、null で外せる。別の商品の画像は 400")
        void setsVariantImage() throws Exception {
            var productId = productWithVariant();
            var imageId = registerImages(productId, 1).getFirst();
            var variantId = JsonPath.<String>read(detail(productId), "$.variants[0].id");
            var otherImage = registerImages(productWithVariant(), 1).getFirst();

            setVariantImage(productId, variantId, "\"" + imageId + "\"", 2).andExpect(status().isNoContent());
            mockMvc.perform(get(BASE + "/" + productId)).andExpect(jsonPath("$.variants[0].imageId").value(imageId));

            setVariantImage(productId, variantId, "\"" + otherImage + "\"", 3)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_VARIANT_IMAGE"));
            setVariantImage(productId, UNKNOWN_ID, "null", 3).andExpect(status().isNotFound());

            setVariantImage(productId, variantId, "null", 3).andExpect(status().isNoContent());
            mockMvc.perform(get(BASE + "/" + productId)).andExpect(jsonPath("$.variants[0].imageId").doesNotExist());
        }

        @Test
        @DisplayName("画像を削除すると一覧から消え、バリアントの指定が外れ、配信のファイルも消える")
        void deletesImage() throws Exception {
            var productId = productWithVariant();
            var ids = registerImages(productId, 2);
            var variantId = JsonPath.<String>read(detail(productId), "$.variants[0].id");
            setVariantImage(productId, variantId, "\"" + ids.get(0) + "\"", 3).andExpect(status().isNoContent());
            var url = JsonPath.<String>read(detail(productId), "$.images[0].url");

            deleteImage(productId, ids.get(0), 4).andExpect(status().isNoContent());

            mockMvc.perform(get(BASE + "/" + productId))
                    .andExpect(jsonPath("$.images", hasSize(1)))
                    .andExpect(jsonPath("$.images[0].id").value(ids.get(1)))
                    .andExpect(jsonPath("$.variants[0].imageId").doesNotExist());
            assertEventuallyGone(url);
            deleteImage(productId, ids.get(1), 4).andExpect(status().isConflict());
            deleteImage(productId, UNKNOWN_ID, 5).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("商品を削除すると、その商品の配信のファイルも消える")
        void deletesFilesWithProduct() throws Exception {
            var productId = productWithVariant();
            registerImages(productId, 2);
            var urls = JsonPath.<List<String>>read(detail(productId), "$.images[*].url");

            mockMvc.perform(delete(BASE + "/" + productId).param("version", "3")).andExpect(status().isNoContent());

            for (var url : urls) {
                assertEventuallyGone(url);
            }
        }
    }

    // ============================== 補助 ==============================

    /**
     * 軸がなく、バリアントを 1 つ持つ非公開の商品（バージョン 1）
     */
    private String productWithVariant() throws Exception {
        var code = CODE_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var id = createdId(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                {"productCode": "%s", "name": "T シャツ", "description": "", "taxCategoryId": "%s",
                 "priceExclTax": 1000}""".formatted(code, taxCategoryId)));
        mockMvc.perform(post(BASE + "/" + id + "/variants").contentType(MediaType.APPLICATION_JSON).content("""
                        {"variantCode": "%s-V", "axisValues": [], "version": 0}""".formatted(code)))
                .andExpect(status().isCreated());
        return id;
    }

    /**
     * PNG を count 枚登録し、画像 ID を並びの順に返す（商品のバージョンは 1 から count だけ上がる）
     */
    private List<String> registerImages(String productId, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            addImage(productId, upload(productId, "image/png", PNG), 1 + i).andExpect(status().isCreated());
        }
        return JsonPath.read(detail(productId), "$.images[*].id");
    }

    /**
     * 画像のアップロードを受け付ける
     */
    private ResultActions createUpload(String productId, String contentType, long contentLength) throws Exception {
        return mockMvc.perform(post(BASE + "/" + productId + "/image-uploads")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"contentType": "%s", "contentLength": %d}""".formatted(contentType, contentLength)));
    }

    /**
     * 受け付けて、署名付き URL へ content を PUT する。uploadId を返す
     */
    private String upload(String productId, String contentType, byte[] content) throws Exception {
        var body = createUpload(productId, contentType, content.length)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var request = HttpRequest.newBuilder(URI.create(JsonPath.read(body, "$.uploadUrl")))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
        JsonPath.<Map<String, String>>read(body, "$.headers").forEach(request::header);
        assertThat(http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
        return JsonPath.read(body, "$.uploadId");
    }

    /**
     * 受け付けた画像を登録する
     */
    private ResultActions addImage(String productId, String uploadId, long version) throws Exception {
        return mockMvc.perform(post(BASE + "/" + productId + "/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"uploadId": "%s", "version": %d}""".formatted(uploadId, version)));
    }

    /**
     * 画像を、指定した順に並び替える
     */
    private ResultActions reorder(String productId, long version, String... imageIds) throws Exception {
        var ids = String.join(", ", Arrays.stream(imageIds).map("\"%s\""::formatted).toList());
        return mockMvc.perform(put(BASE + "/" + productId + "/images/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"imageIds": [%s], "version": %d}""".formatted(ids, version)));
    }

    /**
     * 画像を削除する
     */
    private ResultActions deleteImage(String productId, String imageId, long version) throws Exception {
        return mockMvc.perform(delete(BASE + "/" + productId + "/images/" + imageId).param("version", String.valueOf(version)));
    }

    /**
     * バリアントの画像を指定する。画像 ID は JSON の値（文字列か null）で渡す
     */
    private ResultActions setVariantImage(String productId, String variantId, String imageIdJson, long version)
            throws Exception {
        return mockMvc.perform(put(BASE + "/" + productId + "/variants/" + variantId + "/image")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"imageId": %s, "version": %d}""".formatted(imageIdJson, version)));
    }

    /**
     * 商品の詳細を取得し、200 を確かめて本文を返す
     */
    private String detail(String productId) throws Exception {
        return mockMvc.perform(get(BASE + "/" + productId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * 要求を送って 201 を確かめ、応答の本文の ID を返す
     */
    private String createdId(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        var body = mockMvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /**
     * 配信の URL を、認証なしで取得する（だれでも読めることを確かめる）
     */
    private HttpResponse<byte[]> fetch(String url) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    /**
     * 配信のファイルが、コミット後のイベントで消えるのを待つ
     */
    private void assertEventuallyGone(String url) {
        await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                assertThat(fetch(url).statusCode()).as("配信のファイルが消えない：%s", url).isNotEqualTo(200));
    }
}
