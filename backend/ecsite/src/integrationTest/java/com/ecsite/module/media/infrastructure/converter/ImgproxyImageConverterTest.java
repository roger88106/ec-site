package com.ecsite.module.media.infrastructure.converter;

import com.ecsite.IntegrationTest;
import com.ecsite.TestcontainersConfig;
import com.ecsite.module.media.application.port.ImageConverter;
import com.ecsite.module.media.domain.MediaErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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

import static com.ecsite.BusinessErrorAssertions.assertBusinessError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * imgproxy の画像の変換の統合テスト
 *
 * <p>Testcontainers の imgproxy に、SeaweedFS のアップロードのバケットのファイルを変換させる。
 * テスト用の画像は {@code resources/images}（作り方は {@code generate.sh}）。左が赤、右が青の 64x32 で、
 * JPEG・HEIC・AVIF には向き（時計回りに 90 度）、位置情報、撮影機器（{@value #CAMERA}）が付いている。
 */
@IntegrationTest
class ImgproxyImageConverterTest {

    // ============================== 定数 ==============================

    /**
     * テスト用の画像の撮影機器の名前
     */
    private static final String CAMERA = "TestCam";

    /**
     * imgproxy を直接呼ぶ HttpClient
     */
    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    ImageConverter converter;

    @Autowired
    S3Client s3;

    @Value("${ecsite.media.converter.base-url}")
    URI imgproxyUrl;

    // ============================== テスト ==============================

    @Nested
    @DisplayName("WebP への変換")
    class ToWebp {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"source.jpg", "source.png", "source.webp", "source.bmp", "source.heic", "source.avif"})
        @DisplayName("AC-68 受け付ける形式の画像を、WebP に変換する")
        void convertsToWebp(String file) {
            // 操作：アップロードのバケットに置いた画像を変換する
            var webp = converter.convertToWebp(uploaded(file));

            // 確認：RIFF の入れ物の WebP
            assertThat(Arrays.copyOfRange(webp, 0, 4)).isEqualTo("RIFF".getBytes(StandardCharsets.US_ASCII));
            assertThat(Arrays.copyOfRange(webp, 8, 12)).isEqualTo("WEBP".getBytes(StandardCharsets.US_ASCII));
        }

        @ParameterizedTest(name = "{0} → {1}x{2}")
        @CsvSource({
                "source.jpg, 32, 64",
                "source.png, 64, 32",
                "source.webp, 64, 32",
                "source.bmp, 64, 32",
                "source.heic, 32, 64",
                "source.avif, 32, 64",
        })
        @DisplayName("AC-68 変換では、向きの指定を画素に反映する")
        void appliesOrientation(String file, int width, int height) {
            // 操作：アップロードのバケットに置いた画像を変換する
            var webp = converter.convertToWebp(uploaded(file));

            // 確認：向きの指定がある画像は縦横が入れ替わり、ない画像は元のまま
            assertThat(dimensions(webp)).containsExactly(width, height);
        }

        @Test
        @DisplayName("AC-70 壊れたファイルは、変換の失敗になる")
        void failsOnBrokenFile() {
            // 準備：壊れた HEIC を置く
            var uploadKey = uploaded("broken.heic");

            // 操作と確認：業務のエラー IMAGE_CONVERSION_FAILED
            assertBusinessError(() -> converter.convertToWebp(uploadKey), MediaErrorCode.IMAGE_CONVERSION_FAILED);
        }
    }

    @Nested
    @DisplayName("メタデータの除去")
    class Metadata {

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"source.jpg", "source.heic", "source.avif"})
        @DisplayName("AC-69 撮影機器のメタデータを残さない")
        void stripsCamera(String file) {
            // 操作：撮影機器の付いた画像を変換する
            var exif = exifChunk(converter.convertToWebp(uploaded(file)));

            // 確認：EXIF に撮影機器の名前がない
            assertThat(contains(exif, CAMERA.getBytes(StandardCharsets.US_ASCII))).isFalse();
        }

        @ParameterizedTest(name = "「{0}」")
        @ValueSource(strings = {"source.jpg", "source.heic", "source.avif"})
        @DisplayName("AC-69 位置情報のメタデータを残さない")
        void stripsLocation(String file) {
            // 操作：位置情報の付いた画像を変換する
            var exif = exifChunk(converter.convertToWebp(uploaded(file)));

            // 確認：EXIF に GPS の情報への参照（タグ 0x8825）が、リトルエンディアン・ビッグエンディアンのどちらでもない
            assertThat(contains(exif, new byte[]{0x25, (byte) 0x88})).isFalse();
            assertThat(contains(exif, new byte[]{(byte) 0x88, 0x25})).isFalse();
        }
    }

    @Nested
    @DisplayName("imgproxy の署名")
    class Signature {

        @Test
        @DisplayName("imgproxy は、署名のない URL を拒否する")
        void rejectsUnsignedUrl() throws Exception {
            // 準備：アップロードのバケットに置いた画像を指す、変換元
            var source = sourceOf(uploaded("source.png"));

            // 操作と確認：署名なし（insecure）で呼ぶと 403
            assertThat(status("/insecure/f:webp/" + source)).isEqualTo(403);
        }

        @Test
        @DisplayName("imgproxy は、署名の違う URL を拒否する")
        void rejectsWrongSignature() throws Exception {
            // 準備：アップロードのバケットに置いた画像を指す、変換元
            var source = sourceOf(uploaded("source.png"));

            // 操作と確認：でたらめな署名で呼ぶと 403
            assertThat(status("/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/f:webp/" + source)).isEqualTo(403);
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * WebP の画素数（幅、高さ）を読む。
     *
     * <p>VP8X（拡張）、VP8（非可逆）、VP8L（可逆）のボックスから読む。
     *
     * @param webp WebP
     * @return 幅と高さ
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
            default -> throw new AssertionError("WebP のボックスを読めない：" + chunk);
        };
    }

    /**
     * WebP の EXIF のボックスの中身を読む。
     *
     * @param webp WebP
     * @return EXIF の中身。なければ空
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
            // ボックスの大きさは偶数にそろえる
            offset += 8 + size + (size % 2);
        }
        return new byte[0];
    }

    /**
     * リトルエンディアンの 3 バイトの符号なし整数を読む。
     *
     * @param data   バイト列
     * @param offset 読む位置
     * @return 値
     */
    private static int uint24(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8) | ((data[offset + 2] & 0xFF) << 16);
    }

    /**
     * バイト列の中に、指定したバイトの並びがあるかを返す。
     *
     * @param data バイト列
     * @param part 探す並び
     * @return あれば true
     */
    private static boolean contains(byte[] data, byte[] part) {
        for (int i = 0; i + part.length <= data.length; i++) {
            if (Arrays.equals(data, i, i + part.length, part, 0, part.length)) {
                return true;
            }
        }
        return false;
    }

    // ============================== 準備の補助 ==============================

    /**
     * テスト用の画像をアップロードのバケットに置く。
     *
     * @param file 画像のファイル名
     * @return アップロードのキー
     */
    private String uploaded(String file) {
        var key = UUID.randomUUID() + "/" + UUID.randomUUID();
        s3.putObject(request -> request.bucket(TestcontainersConfig.INCOMING_BUCKET).key(key),
                RequestBody.fromBytes(resource(file)));
        return key;
    }

    /**
     * アップロードのバケットのキーを、imgproxy の変換元（URL セーフの Base64）にする。
     *
     * @param uploadKey アップロードのキー
     * @return 変換元
     */
    private static String sourceOf(String uploadKey) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("s3://" + TestcontainersConfig.INCOMING_BUCKET + "/" + uploadKey).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * テスト用の画像を読む。
     *
     * @param file 画像のファイル名
     * @return 画像の中身
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
     * imgproxy に直接 GET する。
     *
     * @param path パス
     * @return HTTP ステータス
     * @throws IOException          送れない
     * @throws InterruptedException 待つ間に割り込まれた
     */
    private int status(String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(imgproxyUrl + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
