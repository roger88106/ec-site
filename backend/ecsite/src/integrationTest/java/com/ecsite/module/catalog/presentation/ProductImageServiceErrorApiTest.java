package com.ecsite.module.catalog.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.module.media.infrastructure.converter.ImgproxyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品画像の登録で、画像のサービスに届かないときの受け入れテスト
 *
 * <p>画像の変換（imgproxy）は、待ち受けのないポートに向ける（このクラスだけ。変換が成功するテストは {@link ProductImageApiTest}）。
 * ファイル保存（S3）は、本物のクライアントを包み、接続できない失敗を起こす。
 */
@IntegrationTest
@Transactional
class ProductImageServiceErrorApiTest {

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    /**
     * S3 のクライアント（接続できない失敗を起こすため、本物を包む）
     */
    @MockitoSpyBean
    S3Client s3;

    /**
     * 画像の変換の設定（待ち受けのないポートに向けたものに差し替える）
     */
    @TestBean
    ImgproxyProperties converterProperties;

    /**
     * 税区分・商品・画像の受付を作る補助
     */
    private CatalogTestData catalog;

    /**
     * テストで画像を登録する商品
     */
    private CatalogTestData.TestProduct product;

    /**
     * imgproxy を、待ち受けのないポートに向ける（接続を拒まれる）。
     *
     * <p>URL は、テストのコンテナの設定（{@code DynamicPropertyRegistrar}）が上書きするので、設定の値でなく設定の型ごと差し替える。
     * 鍵とソルトは、リクエストが届かないので何でもよい。
     *
     * @return 画像の変換の設定
     */
    static ImgproxyProperties converterProperties() {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ImgproxyProperties(URI.create("http://127.0.0.1:" + port), "00", "00", 82, Duration.ofSeconds(5));
    }

    @BeforeEach
    void setUp() throws Exception {
        catalog = new CatalogTestData(mockMvc, clock);
        product = catalog.product(catalog.taxCategory("画像のサービス " + UUID.randomUUID(), "10"), "T シャツ", 1000);
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("AC-70、296 画像の変換のサービスに接続できなければ、登録は 502 と、やり直せることを示すメッセージを返す")
    void converterUnreachable() throws Exception {
        // 準備：受け付けた画像（変換のサービスには届かない）
        var uploadId = product.upload();

        // 操作と確認：登録は 502 の IMAGE_SERVICE_UNAVAILABLE で、メッセージで再試行を促す
        product.addImage(uploadId, 1)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("IMAGE_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(containsString("やり直して")));
    }

    @Test
    @DisplayName("AC-298 ファイル保存のサービスに接続できなければ、登録は 502 を返す")
    void storageUnreachable() throws Exception {
        // 準備：受け付けた画像と、接続できないファイル保存のサービス
        var uploadId = product.upload();
        doThrow(SdkClientException.create("Unable to execute HTTP request: Connection refused"))
                .when(s3).headObject(any(HeadObjectRequest.class));

        // 操作と確認：登録は 502 の IMAGE_SERVICE_UNAVAILABLE
        product.addImage(uploadId, 1)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("IMAGE_SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("AC-298 アップロードの受付は、ファイル保存のサービスを呼ばない")
    void createUploadWithoutStorage() throws Exception {
        // 準備：ここまでの S3 のクライアントの呼び出しを数えない
        clearInvocations(s3);

        // 操作：アップロードを受け付ける
        catalog.createUpload(product.id, "image/png", 100).andExpect(status().isCreated());

        // 確認：S3 のクライアントを呼んでいない
        verifyNoInteractions(s3);
    }
}
