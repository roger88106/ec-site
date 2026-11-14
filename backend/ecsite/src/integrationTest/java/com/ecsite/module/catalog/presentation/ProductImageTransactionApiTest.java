package com.ecsite.module.catalog.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestJson;
import com.ecsite.module.media.application.port.ImageConverter;
import com.ecsite.module.media.application.port.ObjectStorage;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.media.Tables.ASSET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品画像の登録で、外部サービスをトランザクションの外で呼ぶことの受け入れテスト
 *
 * <p>画像の変換を包み、呼ばれた時点のトランザクションの有無を記録する。ファイル保存を包み、配信のファイルを置いた時点のトランザクションの有無と、
 * 置いたファイルのキーを記録する。
 * テストのトランザクションで包むと変換の時点にトランザクションがあるので、このテストはロールバックせず、作ったデータも消さない。
 */
@IntegrationTest
class ProductImageTransactionApiTest {

    // ============================== 定数 ==============================

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 配信のファイルの取得に使う HTTP クライアント
     */
    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    DSLContext dsl;

    /**
     * 画像の変換（呼ばれた時点のトランザクションの有無を記録するため、本物を包む）
     */
    @MockitoSpyBean
    ImageConverter converter;

    /**
     * ファイルの置き場所（置いた時点のトランザクションの有無と、置いた配信のファイルのキーを記録するため、本物を包む）
     */
    @MockitoSpyBean
    ObjectStorage storage;

    /**
     * 変換が呼ばれた時点で、トランザクションがあったか
     */
    private final List<Boolean> transactionActiveOnConvert = new ArrayList<>();

    /**
     * 配信のファイルを置いた時点で、トランザクションがあったか
     */
    private final List<Boolean> transactionActiveOnPut = new ArrayList<>();

    /**
     * 配信のバケットに置いたファイルのキー
     */
    private final List<String> publishedKeys = new ArrayList<>();

    /**
     * テストで使う税区分の ID
     */
    private UUID taxCategoryId;

    /**
     * テストで画像を登録する商品
     */
    private CatalogTestData.TestProduct product;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(NOW);
        var catalog = new CatalogTestData(mockMvc, clock);
        taxCategoryId = catalog.taxCategory("トランザクションの外 " + UUID.randomUUID(), "10");
        product = catalog.product(taxCategoryId, "T シャツ", 1000);
        doAnswer(invocation -> {
            transactionActiveOnConvert.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(converter).convertToWebp(anyString());
        doAnswer(invocation -> {
            transactionActiveOnPut.add(TransactionSynchronizationManager.isActualTransactionActive());
            publishedKeys.add(invocation.getArgument(0));
            return invocation.callRealMethod();
        }).when(storage).putPublished(anyString(), any(), anyString());
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("外部サービスの呼び出し")
    class OutsideTransaction {

        @Test
        @DisplayName("AC-304 画像の変換は、トランザクションの外で呼ぶ")
        void convertsOutsideTransaction() throws Exception {
            // 準備：受け付けた画像
            var uploadId = product.upload();

            // 操作：登録する
            product.addImage(uploadId, 1).andExpect(status().isCreated());

            // 確認：変換を 1 回、トランザクションのない所で呼んだ
            assertThat(transactionActiveOnConvert).containsExactly(false);
        }

        @Test
        @DisplayName("AC-304 配信のファイルは、トランザクションの外で置く")
        void putsOutsideTransaction() throws Exception {
            // 準備：受け付けた画像
            var uploadId = product.upload();

            // 操作：登録する
            product.addImage(uploadId, 1).andExpect(status().isCreated());

            // 確認：配信のファイルを 1 回、トランザクションのない所で置いた
            assertThat(transactionActiveOnPut).containsExactly(false);
        }

        @Test
        @DisplayName("AC-304 トランザクションを分けても、登録した画像は商品に加わる")
        void addsImageToProduct() throws Exception {
            // 準備：受け付けた画像
            var uploadId = product.upload();

            // 操作：登録する
            var body = product.addImage(uploadId, 1).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            // 確認：商品の画像が、登録した 1 枚だけ
            mockMvc.perform(get("/api/admin/products/" + product.id))
                    .andExpect(jsonPath("$.images.length()").value(1))
                    .andExpect(jsonPath("$.images[*].id", contains(TestJson.createdId(body).toString())));
        }
    }

    @Nested
    @DisplayName("変換の後の保存の失敗")
    class SaveFailure {

        @Test
        @DisplayName("AC-305 変換の後に保存が失敗したら、元のエラー（409）を返す")
        void returnsOriginalError() throws Exception {
            // 準備：変換の間に、ほかの操作が商品を変える
            var uploadId = product.upload();
            changeProductDuringConversion();

            // 操作と確認：保存のときのバージョンの不一致で 409
            product.addImage(uploadId, 1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("AC-305 変換の後に保存が失敗したら、置いた配信のファイルを削除する")
        void discardsPublishedFile() throws Exception {
            // 準備：変換の間に、ほかの操作が商品を変える
            var uploadId = product.upload();
            changeProductDuringConversion();

            // 操作：登録する（保存が失敗する）
            product.addImage(uploadId, 1).andExpect(status().isConflict());

            // 確認（前提）：配信のファイルを 1 つ置いた
            assertThat(publishedKeys).hasSize(1);

            // 確認：置いたファイルを取得すると 404
            var key = publishedKeys.getFirst();
            assertThat(http.send(HttpRequest.newBuilder(storage.publicUrl(key)).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("AC-305 変換の後に保存が失敗したら、アセットを記録しない")
        void recordsNoAsset() throws Exception {
            // 準備：変換の間に、ほかの操作が商品を変える
            var uploadId = product.upload();
            changeProductDuringConversion();

            // 操作：登録する（保存が失敗する）
            product.addImage(uploadId, 1).andExpect(status().isConflict());

            // 確認（前提）：配信のファイルを 1 つ置いた
            assertThat(publishedKeys).hasSize(1);

            // 確認：置いたファイルのアセットの記録がない
            assertThat(dsl.fetchExists(ASSET, ASSET.STORAGE_KEY.eq(publishedKeys.getFirst()))).isFalse();
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * 変換の間に、ほかの操作が商品を変えたことにする（保存のときにバージョンが合わない）。
     *
     * <p>変換を本物で行った後に、商品のバージョンを DB で 1 つ上げる。
     */
    private void changeProductDuringConversion() {
        doAnswer(invocation -> {
            var webp = invocation.callRealMethod();
            dsl.update(PRODUCT).set(PRODUCT.VERSION, PRODUCT.VERSION.plus(1)).where(PRODUCT.ID.eq(product.id)).execute();
            return webp;
        }).when(converter).convertToWebp(anyString());
    }
}
