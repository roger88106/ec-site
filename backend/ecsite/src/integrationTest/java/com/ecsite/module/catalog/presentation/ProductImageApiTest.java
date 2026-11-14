package com.ecsite.module.catalog.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestImages;
import com.ecsite.TestJson;
import com.ecsite.TestcontainersConfig;
import com.ecsite.module.catalog.domain.product.port.ProductRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.ecsite.AsyncEvents.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品画像 API の受け入れテスト
 *
 * <p>ファイルは、受付の応答の署名付き URL へ実際に PUT する（Testcontainers の SeaweedFS）。登録のときに imgproxy で WebP に変換するので、本物の画像を使う。
 * 配信のファイルの削除はコミット後のイベントで行うので、このテストはロールバックせず、作ったデータも消さない（ほかのテストは、一覧の全体ではなく自分のデータだけを確認する）。
 *
 * <p>商品のバージョンは、登録で 1 になる。画像を登録・並び替え・削除するたびと、バリアントに画像を指定するたびに 1 つ上がる。
 */
@IntegrationTest
class ProductImageApiTest {

    // ============================== 定数 ==============================

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
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 画像の大きさの上限
     *
     * <p>10MB。
     */
    private static final int MAX_BYTES = 10 * 1024 * 1024;

    /**
     * PNG の画像
     */
    private static final byte[] PNG = TestImages.png();

    /**
     * JPEG の画像
     */
    private static final byte[] JPEG = TestImages.jpeg();

    /**
     * WebP の画像
     */
    private static final byte[] WEBP = TestImages.of("source.webp");

    /**
     * 配信の URL を取得する HTTP クライアント
     */
    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    S3Client s3;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    TransactionTemplate transaction;

    /**
     * テストで使う税区分の ID
     */
    private UUID taxCategoryId;

    /**
     * 税区分・カテゴリ・商品を作る補助
     */
    private CatalogTestData catalog;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        catalog = new CatalogTestData(mockMvc, clock);
        taxCategoryId = catalog.taxCategory("画像の標準 " + UUID.randomUUID(), "10");
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("受付と登録")
    class Upload {

        @Test
        @DisplayName("AC-37 登録した画像は、商品の画像の最後に加わる")
        void appendsToEnd() throws Exception {
            // 準備：画像のない商品
            var productId = productWithVariant();

            // 操作：PNG、JPEG の順に登録する
            var registered = registerPngThenJpeg(productId);

            // 確認：商品の画像が、登録した順に並ぶ
            mockMvc.perform(get(BASE + "/" + productId))
                    .andExpect(jsonPath("$.images[*].id", contains(registered.getFirst(), registered.get(1))));
        }

        @Test
        @DisplayName("AC-37 画像を登録するごとに、商品のバージョンが上がる")
        void incrementsVersion() throws Exception {
            // 準備：バージョン 1 の商品
            var productId = productWithVariant();

            // 操作：画像を 2 枚登録する
            registerPngThenJpeg(productId);

            // 確認：バージョンが 3
            assertThat(JsonPath.<Integer>read(detail(productId), "$.version")).isEqualTo(3);
        }

        @Test
        @DisplayName("AC-37、68 登録した画像は WebP に変換し、配信の URL でだれでも取得できる")
        void deliversAsWebp() throws Exception {
            // 準備：画像のない商品
            var productId = productWithVariant();

            // 操作：受付の署名付き URL へ PUT した PNG と JPEG を登録する
            registerPngThenJpeg(productId);

            // 確認：配信の URL が WebP のファイルを指し、認証なしで取得できる
            assertDeliveredAsWebp(JsonPath.read(detail(productId), "$.images[*].url"));
        }

        @Test
        @DisplayName("AC-39 WebP も受け付ける")
        void acceptsWebp() throws Exception {
            // 準備：画像のない商品
            var productId = productWithVariant();

            // 操作：WebP を登録する
            catalog.addImage(productId, catalog.upload(productId, "image/webp", WEBP), 1).andExpect(status().isCreated());

            // 確認：WebP の画像が 1 枚加わる
            assertThat(JsonPath.<List<String>>read(detail(productId), "$.images[*].url")).singleElement()
                    .satisfies(url -> assertThat(url).endsWith(".webp"));
        }

        @Test
        @DisplayName("受付は、PUT に付けるヘッダーと期限を返す")
        void returnsUploadInstructions() throws Exception {
            // 準備：画像のない商品
            var productId = productWithVariant();

            // 操作：PNG のアップロードを受け付ける
            var result = catalog.createUpload(productId, "image/png", PNG.length);

            // 確認：受付の ID、PUT の URL・ヘッダー、期限を返す
            assertUploadInstructions(result);
        }

        @Test
        @DisplayName("AC-39、67、272 受け付ける形式以外は、契約の形式の一覧で 400")
        void rejectsUnsupportedFormat() throws Exception {
            // 準備：画像のない商品
            var productId = productWithVariant();

            // 操作と確認：GIF の受付は 400
            catalog.createUpload(productId, "image/gif", 100)
                    .andExpect(status().isBadRequest())
                    // 列挙にない値は本文を読めない誤りになり、項目ごとの誤り（errors）は付かない
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("AC-39、272 10MB ちょうどのファイルは受け付ける")
        void acceptsMaxSize() throws Exception {
            // 準備：画像のない商品
            var productId = productWithVariant();

            // 操作と確認：上限ちょうどの受付は 201
            catalog.createUpload(productId, "image/png", MAX_BYTES).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("AC-39、272 10MB を超えるファイルは、契約の上限で受け付けず 400")
        void rejectsTooLargeFile() throws Exception {
            // 準備：画像のない商品
            var productId = productWithVariant();

            // 操作と確認：上限を 1 バイト超える受付は、大きさの項目の誤りで 400
            catalog.createUpload(productId, "image/png", MAX_BYTES + 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value("contentLength"));
        }

        @Test
        @DisplayName("AC-39 0 バイトのファイルは、業務の検証で受け付けず 400")
        void rejectsEmptyFile() throws Exception {
            // 準備：画像のない商品
            var productId = productWithVariant();

            // 操作と確認：0 バイトの受付は 400
            catalog.createUpload(productId, "image/png", 0)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("IMAGE_TOO_LARGE"));
        }

        @Test
        @DisplayName("AC-39 アップロードしたファイルが 10MB を超えていれば、登録のときに大きさを確認して 400")
        void rejectsOversizedUploadOnRegister() throws Exception {
            // 準備：PNG で受け付けた後に、上限を超えるファイルを置く
            var productId = productWithVariant();
            var body = catalog.createUpload(productId, "image/png", PNG.length)
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            var uploadId = TestJson.uuid(body, "$.uploadId").toString();
            // 署名付き URL は大きさを含むので、ストレージが大きさを検証しない場合に備えた確認を、直接置いて確認する
            s3.putObject(request -> request.bucket(TestcontainersConfig.INCOMING_BUCKET).key(productId + "/" + uploadId),
                    RequestBody.fromBytes(Arrays.copyOf(PNG, MAX_BYTES + 1)));

            // 操作と確認：登録は 400
            catalog.addImage(productId, uploadId, 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("IMAGE_TOO_LARGE"));
        }

        @Test
        @DisplayName("形式を偽ったファイル（中身が画像でない）は、登録のときに先頭のバイトで確認して 400")
        void rejectsDisguisedFile() throws Exception {
            // 準備：PNG と申告して、文字を置く
            var productId = productWithVariant();
            var uploadId = catalog.upload(productId, "image/png", "これは画像ではない".getBytes(StandardCharsets.UTF_8));

            // 操作と確認：登録は 400
            catalog.addImage(productId, uploadId, 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_IMAGE_FILE"));
        }

        @Test
        @DisplayName("形式を偽ったファイルの登録を拒否したとき、画像は加わらない")
        void keepsImagesOnDisguisedFile() throws Exception {
            // 準備：PNG と申告して、文字を置く
            var productId = productWithVariant();
            var uploadId = catalog.upload(productId, "image/png", "これは画像ではない".getBytes(StandardCharsets.UTF_8));

            // 操作：登録する
            catalog.addImage(productId, uploadId, 1).andExpect(status().isBadRequest());

            // 確認：画像がない
            assertThat(JsonPath.<List<String>>read(detail(productId), "$.images")).isEmpty();
        }

        @Test
        @DisplayName("AC-70 変換できない（壊れた）ファイルは、登録のときに 400 IMAGE_CONVERSION_FAILED で拒否する")
        void rejectsUnconvertibleFile() throws Exception {
            // 準備：壊れた HEIC を置く
            var productId = productWithVariant();
            var uploadId = catalog.upload(productId, "image/heic", TestImages.of("broken.heic"));

            // 操作と確認：登録は 400
            catalog.addImage(productId, uploadId, 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("IMAGE_CONVERSION_FAILED"));
        }

        @Test
        @DisplayName("AC-70 変換できないファイルの登録を拒否したとき、画像もバージョンも変わらない")
        void keepsProductOnUnconvertibleFile() throws Exception {
            // 準備：バージョン 1 の商品に、壊れた HEIC を置く
            var productId = productWithVariant();
            var uploadId = catalog.upload(productId, "image/heic", TestImages.of("broken.heic"));

            // 操作：登録する
            catalog.addImage(productId, uploadId, 1).andExpect(status().isBadRequest());

            // 確認：画像がなく、バージョンが 1 のまま
            var detail = detail(productId);
            assertThat(JsonPath.<List<String>>read(detail, "$.images")).isEmpty();
            assertThat(JsonPath.<Integer>read(detail, "$.version")).isEqualTo(1);
        }

        @Test
        @DisplayName("PUT していない uploadId は登録できず 400")
        void rejectsUnknownUpload() throws Exception {
            // 準備：画像のない商品
            var productId = productWithVariant();

            // 操作と確認：受け付けていない uploadId の登録は 400
            catalog.addImage(productId, UUID.randomUUID().toString(), 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("IMAGE_UPLOAD_NOT_FOUND"));
        }

        @Test
        @DisplayName("別の商品で受け付けた uploadId は登録できず 400")
        void rejectsUploadOfAnotherProduct() throws Exception {
            // 準備：別の商品で受け付けて PUT した画像
            var productId = productWithVariant();
            var otherUpload = catalog.upload(productWithVariant(), "image/png", PNG);

            // 操作と確認：この商品への登録は 400
            catalog.addImage(productId, otherUpload, 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("IMAGE_UPLOAD_NOT_FOUND"));
        }

        @Test
        @DisplayName("登録で、取得時のバージョンと一致しなければ 409")
        void rejectsStaleVersion() throws Exception {
            // 準備：バージョン 1 の商品で受け付けた画像
            var productId = productWithVariant();
            var uploadId = catalog.upload(productId, "image/png", PNG);

            // 操作と確認：バージョン 0 での登録は 409
            catalog.addImage(productId, uploadId, 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("存在しない商品の受付は 404")
        void rejectsUploadForUnknownProduct() throws Exception {
            // 操作と確認：見つからないので 404
            catalog.createUpload(UUID.fromString(UNKNOWN_ID), "image/png", 100).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("存在しない商品への登録は 404")
        void rejectsRegisterForUnknownProduct() throws Exception {
            // 準備：ほかの商品で受け付けた画像
            var uploadId = catalog.upload(productWithVariant(), "image/png", PNG);

            // 操作と確認：見つからないので 404
            catalog.addImage(UUID.fromString(UNKNOWN_ID), uploadId, 0).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("枚数の上限")
    class Limit {

        @Test
        @DisplayName("AC-40 20 枚登録した商品は、受付で 400")
        void rejectsUploadOverLimit() throws Exception {
            // 準備：20 枚の画像を持つ商品
            var productId = productWithVariant();
            var version = withNineteenImages(productId);
            catalog.addImage(productId, catalog.upload(productId, "image/png", PNG), version).andExpect(status().isCreated());

            // 操作と確認：21 枚目の受付は 400
            catalog.createUpload(productId, "image/png", PNG.length)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TOO_MANY_PRODUCT_IMAGES"));
        }

        @Test
        @DisplayName("AC-40 20 枚目を登録する前に受け付けておいた画像は、登録のときの再確認で 400")
        void rejectsRegisterOverLimit() throws Exception {
            // 準備：19 枚の画像を持つ商品で 1 枚を受け付けておき、その後に 20 枚目を登録する
            var productId = productWithVariant();
            var version = withNineteenImages(productId);
            var pending = catalog.upload(productId, "image/png", PNG);
            catalog.addImage(productId, catalog.upload(productId, "image/png", PNG), version).andExpect(status().isCreated());

            // 操作と確認：受け付けておいた画像の登録は 400
            catalog.addImage(productId, pending, version + 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("TOO_MANY_PRODUCT_IMAGES"));
        }
    }

    @Nested
    @DisplayName("並び替え")
    class Reorder {

        @Test
        @DisplayName("AC-37 画像を、送った順に並べ替えられる")
        void reorders() throws Exception {
            // 準備：画像 3 枚の商品（バージョン 4）
            var productId = productWithVariant();
            var ids = registerImages(productId, 3);

            // 操作：3・1・2 枚目の順に並べ替える
            reorder(productId, 4, ids.get(2), ids.getFirst(), ids.get(1)).andExpect(status().isNoContent());

            // 確認：商品の画像が送った順
            mockMvc.perform(get(BASE + "/" + productId))
                    .andExpect(jsonPath("$.images[*].id", contains(ids.get(2), ids.getFirst(), ids.get(1))));
        }

        @Test
        @DisplayName("AC-37 並べ替えで送った画像に過不足があれば 400")
        void rejectsMissingImage() throws Exception {
            // 準備：画像 3 枚の商品（バージョン 4）
            var productId = productWithVariant();
            var ids = registerImages(productId, 3);

            // 操作と確認：3 枚目を抜いて送ると 400
            reorder(productId, 4, ids.getFirst(), ids.get(1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_IMAGE_ORDER"));
        }

        @Test
        @DisplayName("並べ替えで、取得時のバージョンと一致しなければ 409")
        void rejectsStaleVersion() throws Exception {
            // 準備：画像 3 枚の商品（バージョン 4）
            var productId = productWithVariant();
            var ids = registerImages(productId, 3);

            // 操作と確認：古いバージョン 3 で送ると 409
            reorder(productId, 3, ids.getFirst(), ids.get(1), ids.get(2)).andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("バリアントの画像")
    class VariantImage {

        @Test
        @DisplayName("AC-38 バリアントにその商品の画像を指定できる")
        void setsVariantImage() throws Exception {
            // 準備：画像 1 枚の商品（バージョン 2）
            var productId = productWithVariant();
            var imageId = registerImages(productId, 1).getFirst();
            var variantId = variantIdOf(productId);

            // 操作：バリアントに画像を指定する
            setVariantImage(productId, variantId, "\"" + imageId + "\"", 2).andExpect(status().isNoContent());

            // 確認：バリアントの画像が指定した画像
            mockMvc.perform(get(BASE + "/" + productId)).andExpect(jsonPath("$.variants[0].imageId").value(imageId));
        }

        @Test
        @DisplayName("AC-38 null を送ると、バリアントの画像の指定が外れる")
        void clearsVariantImage() throws Exception {
            // 準備：バリアントに画像を指定した商品（バージョン 3）
            var productId = productWithVariant();
            var imageId = registerImages(productId, 1).getFirst();
            var variantId = variantIdOf(productId);
            setVariantImage(productId, variantId, "\"" + imageId + "\"", 2).andExpect(status().isNoContent());

            // 操作：null を送る
            setVariantImage(productId, variantId, "null", 3).andExpect(status().isNoContent());

            // 確認：バリアントの画像がない
            mockMvc.perform(get(BASE + "/" + productId)).andExpect(jsonPath("$.variants[0].imageId").doesNotExist());
        }

        @Test
        @DisplayName("AC-38 別の商品の画像は、バリアントに指定できず 400")
        void rejectsImageOfAnotherProduct() throws Exception {
            // 準備：画像 1 枚の商品（バージョン 2）と、別の商品の画像
            var productId = productWithVariant();
            registerImages(productId, 1);
            var variantId = variantIdOf(productId);
            var otherImage = registerImages(productWithVariant(), 1).getFirst();

            // 操作と確認：別の商品の画像の指定は 400
            setVariantImage(productId, variantId, "\"" + otherImage + "\"", 2)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_VARIANT_IMAGE"));
        }

        @Test
        @DisplayName("存在しないバリアントへの画像の指定は 404")
        void rejectsUnknownVariant() throws Exception {
            // 準備：画像 1 枚の商品（バージョン 2）
            var productId = productWithVariant();
            registerImages(productId, 1);

            // 操作と確認：見つからないので 404
            setVariantImage(productId, UNKNOWN_ID, "null", 2).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("削除")
    class Delete {

        @Test
        @DisplayName("画像を削除すると、商品の画像から消える")
        void deletesImage() throws Exception {
            // 準備：画像 2 枚の商品（バージョン 3）
            var productId = productWithVariant();
            var ids = registerImages(productId, 2);

            // 操作：1 枚目を削除する
            deleteImage(productId, ids.getFirst(), 3).andExpect(status().isNoContent());

            // 確認：2 枚目だけが残る
            mockMvc.perform(get(BASE + "/" + productId))
                    .andExpect(jsonPath("$.images", hasSize(1)))
                    .andExpect(jsonPath("$.images[0].id").value(ids.get(1)));
        }

        @Test
        @DisplayName("画像を削除すると、その画像を指定したバリアントの指定が外れる")
        void clearsVariantImageOnDelete() throws Exception {
            // 準備：画像 2 枚の商品で、バリアントに 1 枚目を指定する（バージョン 4）
            var productId = productWithVariant();
            var ids = registerImages(productId, 2);
            setVariantImage(productId, variantIdOf(productId), "\"" + ids.getFirst() + "\"", 3).andExpect(status().isNoContent());

            // 操作：1 枚目を削除する
            deleteImage(productId, ids.getFirst(), 4).andExpect(status().isNoContent());

            // 確認：バリアントの画像がない
            mockMvc.perform(get(BASE + "/" + productId)).andExpect(jsonPath("$.variants[0].imageId").doesNotExist());
        }

        @Test
        @DisplayName("画像を削除すると、配信のファイルも消える")
        void deletesFileWithImage() throws Exception {
            // 準備：画像 2 枚の商品（バージョン 3）と、1 枚目の配信の URL
            var productId = productWithVariant();
            var ids = registerImages(productId, 2);
            var url = JsonPath.<String>read(detail(productId), "$.images[0].url");

            // 操作：1 枚目を削除する
            deleteImage(productId, ids.getFirst(), 3).andExpect(status().isNoContent());

            // 確認：配信の URL で取得できなくなる
            assertEventuallyGone(url);
        }

        @Test
        @DisplayName("画像の削除で、取得時のバージョンと一致しなければ 409")
        void rejectsStaleVersion() throws Exception {
            // 準備：画像 2 枚の商品（バージョン 3）
            var productId = productWithVariant();
            var ids = registerImages(productId, 2);

            // 操作と確認：古いバージョン 2 での削除は 409
            deleteImage(productId, ids.get(1), 2).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("存在しない画像の削除は 404")
        void rejectsUnknownImage() throws Exception {
            // 準備：画像 1 枚の商品（バージョン 2）
            var productId = productWithVariant();
            registerImages(productId, 1);

            // 操作と確認：見つからないので 404
            deleteImage(productId, UNKNOWN_ID, 2).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("商品を削除すると、その商品の配信のファイルも消える")
        void deletesFilesWithProduct() throws Exception {
            // 準備：画像 2 枚の商品（バージョン 3）と、配信の URL
            var productId = productWithVariant();
            registerImages(productId, 2);
            var urls = JsonPath.<List<String>>read(detail(productId), "$.images[*].url");

            // 操作：商品を削除する
            mockMvc.perform(delete(BASE + "/" + productId).param("version", "3")).andExpect(status().isNoContent());

            // 確認：どの配信の URL でも取得できなくなる
            for (var url : urls) {
                assertEventuallyGone(url);
            }
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 登録した 2 枚の画像が、WebP に変換して配信されていることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>配信の URL が 2 つ</li>
     *   <li>先頭の URL を認証なしで取得すると 200</li>
     *   <li>取得した中身が WebP（RIFF の形式の名前が WEBP）</li>
     *   <li>Content-Type が image/webp</li>
     *   <li>どの URL も WebP のファイル（拡張子 .webp）を指す</li>
     * </ul>
     *
     * @param urls 商品の画像の配信の URL（並びの順）
     */
    private void assertDeliveredAsWebp(List<String> urls) throws IOException, InterruptedException {
        assertThat(urls).hasSize(2);
        var delivered = fetch(urls.getFirst());
        assertThat(delivered.statusCode()).isEqualTo(200);
        assertThat(new String(delivered.body(), 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        assertThat(delivered.headers().firstValue("Content-Type")).contains("image/webp");
        assertThat(urls).allSatisfy(url -> assertThat(url).endsWith(".webp"));
    }

    /**
     * PNG の受付の応答が、PUT の手順を返していることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>201</li>
     *   <li>受付の ID がある</li>
     *   <li>PUT する URL がある</li>
     *   <li>PUT に付けるヘッダーの Content-Type が image/png</li>
     *   <li>期限がある</li>
     * </ul>
     *
     * @param result 受付の応答
     */
    private static void assertUploadInstructions(ResultActions result) throws Exception {
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadId").isNotEmpty())
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.headers.Content-Type").value("image/png"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    /**
     * 配信のファイルが、コミット後のイベントで消えるのを待つ。
     *
     * @param url 配信の URL
     */
    private void assertEventuallyGone(String url) {
        await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                assertThat(fetch(url).statusCode()).as("配信のファイルが消えない：%s", url).isNotEqualTo(200));
    }

    // ============================== 準備の補助 ==============================

    /**
     * 軸がなく、バリアントを 1 つ持つ非公開の商品（バージョン 1）を作る。
     *
     * @return 商品 ID
     */
    private UUID productWithVariant() throws Exception {
        var code = CODE_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return catalog.product(code, taxCategoryId, "T シャツ", 1000).id;
    }

    /**
     * 画像のない商品に、PNG、JPEG の順に画像を登録する（商品のバージョンは 3 になる）。
     *
     * <p>登録の応答が 201 で、画像 ID を返すことも確認する。
     *
     * @param productId 商品 ID（バージョン 1）
     * @return 登録の応答が返した画像 ID（登録した順）
     */
    private List<String> registerPngThenJpeg(UUID productId) throws Exception {
        var first = catalog.addImage(productId, catalog.upload(productId, "image/png", PNG), 1)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        var second = catalog.addImage(productId, catalog.upload(productId, "image/jpeg", JPEG), 2)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return List.of(TestJson.createdId(first).toString(), TestJson.createdId(second).toString());
    }

    /**
     * 上限の手前の 19 枚の画像を、Repository から商品に入れる。
     *
     * <p>ファイルを使わないので、API で 19 回送らずに入れる（API で送ると遅い）。
     *
     * @param productId 商品 ID
     * @return 入れた後の商品のバージョン
     */
    private long withNineteenImages(UUID productId) {
        transaction.executeWithoutResult(status -> {
            var product = productRepository.findById(productId).orElseThrow();
            for (int i = 1; i <= 19; i++) {
                product.addImage(UUID.randomUUID(), UUID.randomUUID());
            }
            productRepository.save(product);
        });
        return productRepository.findById(productId).orElseThrow().version();
    }

    /**
     * PNG を count 枚登録し、画像 ID を並びの順に返す（商品のバージョンは 1 から count だけ上がる）。
     *
     * @param productId 商品 ID（バージョン 1）
     * @param count     枚数
     * @return 画像 ID（並びの順）
     */
    private List<String> registerImages(UUID productId, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            catalog.addImage(productId, catalog.upload(productId, "image/png", PNG), 1 + i).andExpect(status().isCreated());
        }
        return JsonPath.read(detail(productId), "$.images[*].id");
    }

    /**
     * 商品の 1 つ目のバリアントの ID を読む。
     *
     * @param productId 商品 ID
     * @return バリアント ID
     */
    private String variantIdOf(UUID productId) throws Exception {
        return JsonPath.read(detail(productId), "$.variants[0].id");
    }

    /**
     * 画像を、指定した順に並び替える。
     *
     * @param productId 商品 ID
     * @param version   取得時の商品のバージョン
     * @param imageIds  並べたい画像 ID
     * @return 応答
     */
    private ResultActions reorder(UUID productId, long version, String... imageIds) throws Exception {
        var ids = String.join(", ", Arrays.stream(imageIds).map("\"%s\""::formatted).toList());
        return mockMvc.perform(put(BASE + "/" + productId + "/images/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"imageIds": [%s], "version": %d}""".formatted(ids, version)));
    }

    /**
     * 画像を削除する。
     *
     * @param productId 商品 ID
     * @param imageId   画像 ID
     * @param version   取得時の商品のバージョン
     * @return 応答
     */
    private ResultActions deleteImage(UUID productId, String imageId, long version) throws Exception {
        return mockMvc.perform(delete(BASE + "/" + productId + "/images/" + imageId).param("version", String.valueOf(version)));
    }

    /**
     * バリアントの画像を指定する。
     *
     * @param productId   商品 ID
     * @param variantId   バリアント ID
     * @param imageIdJson 画像 ID の JSON の値（文字列か null）
     * @param version     取得時の商品のバージョン
     * @return 応答
     */
    private ResultActions setVariantImage(UUID productId, String variantId, String imageIdJson, long version)
            throws Exception {
        return mockMvc.perform(put(BASE + "/" + productId + "/variants/" + variantId + "/image")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"imageId": %s, "version": %d}""".formatted(imageIdJson, version)));
    }

    /**
     * 商品の詳細を取得し、200 を確認して本文を返す。
     *
     * @param productId 商品 ID
     * @return 応答の本文
     */
    private String detail(UUID productId) throws Exception {
        return mockMvc.perform(get(BASE + "/" + productId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * 配信の URL を、認証なしで取得する（だれでも読めることを確認する）。
     *
     * @param url 配信の URL
     * @return 応答
     */
    private HttpResponse<byte[]> fetch(String url) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
