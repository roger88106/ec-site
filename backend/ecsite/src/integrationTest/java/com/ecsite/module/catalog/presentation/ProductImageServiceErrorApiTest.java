package com.ecsite.module.catalog.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import com.ecsite.TestClockConfig;
import com.ecsite.TestImages;
import com.ecsite.module.media.infrastructure.converter.ImgproxyProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品画像の登録で、画像のサービスに届かないときの受け入れテスト（T017-03-09、US-63）。<br />
 * 画像の変換（imgproxy）は、待ち受けのないポートに向ける（このクラスだけ。変換が成功するテストは {@link ProductImageApiTest}）。
 * ファイル保存（S3）は、本物のクライアントを包み、接続できない失敗を起こす。
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@Transactional
class ProductImageServiceErrorApiTest {

    private static final byte[] PNG = TestImages.png();

    private final HttpClient http = HttpClient.newHttpClient();

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

    private CatalogTestData.TestProduct product;

    /**
     * imgproxy を、待ち受けのないポートに向ける（接続を拒まれる）。
     * URL は、テストのコンテナの設定（{@code DynamicPropertyRegistrar}）が上書きするので、設定の値でなく設定の型ごと差し替える。
     * 鍵とソルトは、要求が届かないので何でもよい。
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
        var data = new CatalogTestData(mockMvc, clock);
        product = data.product(data.taxCategory("画像のサービス " + UUID.randomUUID(), "10"), "T シャツ", 1000);
    }

    @Test
    @DisplayName("AC-296 画像の変換のサービスに接続できなければ、登録は 502 と、やり直せることを示すメッセージを返す")
    void converterUnreachable() throws Exception {
        var uploadId = upload();

        addImage(uploadId)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("IMAGE_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(containsString("やり直して")));
    }

    @Test
    @DisplayName("AC-298 ファイル保存のサービスに接続できなければ、登録は 502 を返す")
    void storageUnreachable() throws Exception {
        var uploadId = upload();
        doThrow(SdkClientException.create("Unable to execute HTTP request: Connection refused"))
                .when(s3).headObject(any(HeadObjectRequest.class));

        addImage(uploadId)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("IMAGE_SERVICE_UNAVAILABLE"));
    }

    /**
     * PNG を受け付けて、署名付き URL へ PUT する。uploadId を返す
     */
    private String upload() throws Exception {
        var body = mockMvc.perform(post("/api/admin/products/" + product.id + "/image-uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contentType": "image/png", "contentLength": %d}""".formatted(PNG.length)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var request = HttpRequest.newBuilder(URI.create(JsonPath.read(body, "$.uploadUrl")))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(PNG));
        JsonPath.<Map<String, String>>read(body, "$.headers").forEach(request::header);
        assertThat(http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
        return JsonPath.read(body, "$.uploadId");
    }

    /**
     * 受け付けた画像を登録する（商品はバリアントを 1 つ追加した後なので、バージョンは 1）
     */
    private ResultActions addImage(String uploadId) throws Exception {
        return mockMvc.perform(post("/api/admin/products/" + product.id + "/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"uploadId": "%s", "version": 1}""".formatted(uploadId)));
    }
}
