package com.ecsite.module.catalog.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.module.media.application.port.ImageConverter;
import com.ecsite.module.media.application.port.ObjectStorage;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;
import static com.ecsite.jooq.media.Tables.ASSET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品画像の登録で、外部サービスをトランザクションの外で呼ぶことの受け入れテスト。<br />
 * 画像の変換を包み、呼ばれた時点のトランザクションの有無を記録する。ファイル保存を包み、置いた配信のファイルのキーを記録する。
 * テストのトランザクションで包むと変換の時点にトランザクションがあるので、このテストはロールバックせず、作ったデータも消さない。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class ProductImageTransactionApiTest {

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
     * ファイルの置き場所（置いた配信のファイルのキーを記録するため、本物を包む）
     */
    @MockitoSpyBean
    ObjectStorage storage;

    /**
     * 変換が呼ばれた時点で、トランザクションがあったか
     */
    private final List<Boolean> transactionActiveOnConvert = new ArrayList<>();

    /**
     * 配信のバケットに置いたファイルのキー
     */
    private final List<String> publishedKeys = new ArrayList<>();

    /**
     * テストで使う税区分の ID
     */
    private UUID taxCategoryId;

    private CatalogTestData.TestProduct product;

    @BeforeEach
    void setUp() throws Exception {
        var data = new CatalogTestData(mockMvc, clock);
        taxCategoryId = data.taxCategory("トランザクションの外 " + UUID.randomUUID(), "10");
        product = data.product(taxCategoryId, "T シャツ", 1000);
        doAnswer(invocation -> {
            transactionActiveOnConvert.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(converter).convertToWebp(anyString());
        doAnswer(invocation -> {
            publishedKeys.add(invocation.getArgument(0));
            return invocation.callRealMethod();
        }).when(storage).putPublished(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("AC-304 画像の変換はトランザクションの外で呼び、登録した画像は商品の最後に加わる")
    void convertsOutsideTransaction() throws Exception {
        var uploadId = product.upload();

        product.addImage(uploadId, 1).andExpect(status().isCreated());

        assertThat(transactionActiveOnConvert).containsExactly(false);
        mockMvc.perform(get("/api/admin/products/" + product.id))
                .andExpect(jsonPath("$.images.length()").value(1));
    }

    @Test
    @DisplayName("AC-305 変換の後に保存が失敗したら、配信のファイルを消し、アセットを記録せず、元のエラー（409）を返す")
    void discardsFileWhenSaveFails() throws Exception {
        var uploadId = product.upload();
        // 変換の間に、ほかの操作が商品を変えた（保存のときにバージョンが合わない）
        doAnswer(invocation -> {
            var webp = invocation.callRealMethod();
            dsl.update(PRODUCT).set(PRODUCT.VERSION, PRODUCT.VERSION.plus(1)).where(PRODUCT.ID.eq(product.id)).execute();
            return webp;
        }).when(converter).convertToWebp(anyString());

        product.addImage(uploadId, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));

        assertThat(publishedKeys).hasSize(1);
        var key = publishedKeys.getFirst();
        assertThat(dsl.fetchExists(ASSET, ASSET.STORAGE_KEY.eq(key))).isFalse();
        assertThat(http.send(HttpRequest.newBuilder(storage.publicUrl(key)).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
    }
}
