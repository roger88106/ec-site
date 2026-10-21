package com.ecsite.module.media.infrastructure.converter;

import com.ecsite.IntegrationTest;
import com.ecsite.TestcontainersConfig;
import com.ecsite.common.error.exception.BusinessException;
import com.ecsite.module.media.application.port.ImageConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 画像の変換の imgproxy の実装の統合テスト（T1035。AC-68〜70。plan 001 第 7.5 節）。<br />
 * Testcontainers の imgproxy に、SeaweedFS のアップロードのバケットのファイルを変換させる。
 * テスト用の画像は {@code resources/images}（作り方は {@code generate.sh}）。左が赤、右が青の 64x32 で、
 * JPEG・HEIC・AVIF には向き（時計回りに 90 度）、位置情報、撮影機器（TestCam）が付いている。
 */
@IntegrationTest
class ImgproxyImageConverterTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    ImageConverter converter;

    @Autowired
    S3Client s3;

    @Value("${ecsite.media.converter.base-url}")
    URI imgproxyUrl;

    @ParameterizedTest(name = "{0} → WebP（{1}x{2}）")
    @CsvSource({
            "source.jpg, 32, 64",
            "source.png, 64, 32",
            "source.webp, 64, 32",
            "source.bmp, 64, 32",
            "source.heic, 32, 64",
            "source.avif, 32, 64",
    })
    @DisplayName("AC-68 各形式の画像を WebP に変換し、向きを画素に反映する")
    void convertsToWebp(String file, int width, int height) {
        var webp = converter.convertToWebp(uploaded(file));

        assertThat(Arrays.copyOfRange(webp, 0, 4)).isEqualTo("RIFF".getBytes(StandardCharsets.US_ASCII));
        assertThat(Arrays.copyOfRange(webp, 8, 12)).isEqualTo("WEBP".getBytes(StandardCharsets.US_ASCII));
        assertThat(dimensions(webp)).containsExactly(width, height);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"source.jpg", "source.heic", "source.avif"})
    @DisplayName("AC-69 位置情報と撮影機器のメタデータを残さない")
    void stripsMetadata(String file) {
        var exif = exifChunk(converter.convertToWebp(uploaded(file)));

        assertThat(contains(exif, "TestCam".getBytes(StandardCharsets.US_ASCII))).isFalse();
        // GPS の情報への参照（タグ 0x8825）。リトルエンディアン・ビッグエンディアンのどちらでもない
        assertThat(contains(exif, new byte[]{0x25, (byte) 0x88})).isFalse();
        assertThat(contains(exif, new byte[]{(byte) 0x88, 0x25})).isFalse();
    }

    @Test
    @DisplayName("AC-70 壊れたファイルは、変換の失敗になる")
    void failsOnBrokenFile() {
        var uploadKey = uploaded("broken.heic");

        assertThatThrownBy(() -> converter.convertToWebp(uploadKey))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorDetail().errorCode().code()).isEqualTo("IMAGE_CONVERSION_FAILED"));
    }

    @Test
    @DisplayName("imgproxy は、署名のない URL、署名の違う URL を拒否する")
    void imgproxyRejectsUnsignedUrl() throws Exception {
        var uploadKey = uploaded("source.png");
        var source = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("s3://" + TestcontainersConfig.INCOMING_BUCKET + "/" + uploadKey).getBytes(StandardCharsets.UTF_8));

        assertThat(status("/insecure/f:webp/" + source)).isEqualTo(403);
        assertThat(status("/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/f:webp/" + source)).isEqualTo(403);
    }

    // ============================== 補助 ==============================

    /**
     * テスト用の画像をアップロードのバケットに置き、そのキーを返す
     */
    private String uploaded(String file) {
        var key = UUID.randomUUID() + "/" + UUID.randomUUID();
        s3.putObject(request -> request.bucket(TestcontainersConfig.INCOMING_BUCKET).key(key),
                RequestBody.fromBytes(resource(file)));
        return key;
    }

    /**
     * テスト用の画像を読む
     */
    private static byte[] resource(String file) {
        try (var in = ImgproxyImageConverterTest.class.getResourceAsStream("/images/" + file)) {
            assertThat(in).as(file).isNotNull();
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * imgproxy に直接 GET し、HTTP ステータスを返す
     */
    private int status(String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(imgproxyUrl + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /**
     * WebP の画素数（幅、高さ）。VP8X（拡張）、VP8（非可逆）、VP8L（可逆）の箱から読む
     */
    private static int[] dimensions(byte[] webp) {
        var buffer = ByteBuffer.wrap(webp).order(ByteOrder.LITTLE_ENDIAN);
        var chunk = new String(webp, 12, 4, StandardCharsets.US_ASCII);
        return switch (chunk) {
            case "VP8X" -> new int[]{uint24(webp, 24) + 1, uint24(webp, 27) + 1};
            case "VP8 " -> new int[]{buffer.getShort(26) & 0x3FFF, buffer.getShort(28) & 0x3FFF};
            case "VP8L" -> {
                var bits = buffer.getInt(21);
                yield new int[]{(bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1};
            }
            default -> throw new AssertionError("WebP の箱を読めない：" + chunk);
        };
    }

    /**
     * WebP の EXIF の箱の中身。なければ空
     */
    private static byte[] exifChunk(byte[] webp) {
        var buffer = ByteBuffer.wrap(webp).order(ByteOrder.LITTLE_ENDIAN);
        var offset = 12;
        while (offset + 8 <= webp.length) {
            var name = new String(webp, offset, 4, StandardCharsets.US_ASCII);
            var size = buffer.getInt(offset + 4);
            if (name.equals("EXIF")) {
                return Arrays.copyOfRange(webp, offset + 8, offset + 8 + size);
            }
            // 箱の大きさは偶数にそろえる
            offset += 8 + size + (size % 2);
        }
        return new byte[0];
    }

    /**
     * リトルエンディアンの 3 バイトの符号なし整数を読む
     */
    private static int uint24(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8) | ((data[offset + 2] & 0xFF) << 16);
    }

    /**
     * バイト列の中に、指定したバイトの並びがあるかを返す
     */
    private static boolean contains(byte[] data, byte[] part) {
        for (int i = 0; i + part.length <= data.length; i++) {
            if (Arrays.equals(data, i, i + part.length, part, 0, part.length)) {
                return true;
            }
        }
        return false;
    }
}
