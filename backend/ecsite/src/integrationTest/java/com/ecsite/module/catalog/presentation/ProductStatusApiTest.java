package com.ecsite.module.catalog.presentation;

import com.ecsite.CatalogTestData;
import com.ecsite.IntegrationTest;
import com.ecsite.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品 API の受け入れテスト（公開・非公開・販売終了・削除・公開期間）
 *
 * <p>商品の状態の切り替え、削除、公開期間を確認する。C 側への表示は {@code StorefrontApiTest} が確認する。
 *
 * <p>現在時刻は {@link MutableClock} で固定する。税区分はテストごとに作る。テストごとにロールバックする。
 *
 * <p>準備のデータと確認の期待値は、同じ定数から作る（準備した物から期待値を取り出さない）。
 */
@IntegrationTest
@Transactional
class ProductStatusApiTest {

    // ============================== 定数 ==============================

    /**
     * 存在しない ID
     */
    private static final UUID UNKNOWN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /**
     * テストの現在時刻
     *
     * <p>日本時間の 2026-10-08 00:00。
     */
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    /**
     * 商品の品番
     */
    private static final String PRODUCT_CODE = "ABC-001";

    /**
     * 公開開始日時（送る形）
     *
     * <p>日本時間の 2026-10-10 00:00。テストの現在時刻より後。
     */
    private static final String PUBLISH_START = "2026-10-10T00:00:00+09:00";

    /**
     * 公開開始日時（応答の形）
     *
     * <p>{@link #PUBLISH_START} を UTC で表したもの。
     */
    private static final String PUBLISH_START_UTC = "2026-10-09T15:00:00Z";

    /**
     * 公開終了日時（送る形）
     *
     * <p>日本時間の 2026-10-20 00:00。
     */
    private static final String PUBLISH_END = "2026-10-20T00:00:00+09:00";

    /**
     * 公開終了日時（応答の形）
     *
     * <p>{@link #PUBLISH_END} を UTC で表したもの。
     */
    private static final String PUBLISH_END_UTC = "2026-10-19T15:00:00Z";

    // ============================== Bean ==============================

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    /**
     * 商品 API を呼ぶ補助
     */
    private ProductRequests products;

    @BeforeEach
    void setUp() throws Exception {
        var standard = new CatalogTestData(mockMvc, clock)
                .taxCategory("状態 " + UUID.randomUUID().toString().substring(0, 8), "10").toString();
        products = new ProductRequests(mockMvc, standard);
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("公開")
    class Publish {

        @Test
        @DisplayName("AC-29 公開すると、状態が「公開」になる")
        void publishes() throws Exception {
            // 準備：バリアントを持つ非公開の商品
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作：公開する
            products.publish(id).andExpect(status().isNoContent());

            // 確認：状態が「公開」
            products.getProduct(id).andExpect(jsonPath("$.status").value("PUBLISHED"));
        }

        @Test
        @DisplayName("AC-54 公開すると、公開した時刻が初回表示日時として記録される")
        void recordsFirstShownAt() throws Exception {
            // 準備：バリアントを持つ非公開の商品
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作：公開する
            products.publish(id).andExpect(status().isNoContent());

            // 確認：初回表示日時がテストの現在時刻
            products.getProduct(id).andExpect(jsonPath("$.firstShownAt").value(NOW.toString()));
        }

        @Test
        @DisplayName("公開すると、バージョンが上がる")
        void incrementsVersion() throws Exception {
            // 準備：バリアントを持ち、バージョンが 1 の商品
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作：公開する
            products.publish(id).andExpect(status().isNoContent());

            // 確認：バージョンが 2
            products.getProduct(id).andExpect(jsonPath("$.version").value(2));
        }

        @Test
        @DisplayName("公開中の商品を公開しても 204 で、何も変わらない")
        void publishesIdempotently() throws Exception {
            // 準備：公開して、バージョンが 2 になった商品
            var id = products.productWithVariant(PRODUCT_CODE);
            products.publish(id).andExpect(status().isNoContent());

            // 操作：もう一度公開する
            products.publish(id).andExpect(status().isNoContent());

            // 確認：バージョンが 2 のまま
            products.getProduct(id).andExpect(jsonPath("$.version").value(2));
        }

        @Test
        @DisplayName("AC-31 バリアントのない商品は公開できず 400")
        void rejectsPublishingWithoutVariants() throws Exception {
            // 準備：バリアントのない商品
            var id = products.product(PRODUCT_CODE);

            // 操作と確認：バリアントがないので拒否される
            products.publish(id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PRODUCT_HAS_NO_VARIANTS"));
        }

        @Test
        @DisplayName("存在しない商品の状態の切り替えは 404")
        void rejectsUnknownProduct() throws Exception {
            // 操作と確認：見つからない
            products.publish(UNKNOWN_ID)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("非公開")
    class Unpublish {

        @Test
        @DisplayName("AC-30 公開中の商品を非公開にできる")
        void unpublishes() throws Exception {
            // 準備：公開中の商品
            var id = products.productWithVariant(PRODUCT_CODE);
            products.publish(id).andExpect(status().isNoContent());

            // 操作：非公開にする
            products.unpublish(id).andExpect(status().isNoContent());

            // 確認：状態が「非公開」
            products.getProduct(id).andExpect(jsonPath("$.status").value("UNPUBLISHED"));
        }

        @Test
        @DisplayName("非公開の商品を非公開にしても 204")
        void unpublishesIdempotently() throws Exception {
            // 準備：非公開の商品
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作と確認：受け付けられる
            products.unpublish(id).andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("販売終了")
    class Discontinue {

        @Test
        @DisplayName("AC-32 販売終了にできる")
        void discontinues() throws Exception {
            // 準備：公開中の商品
            var id = publishedProduct();

            // 操作：販売終了にする
            products.discontinue(id).andExpect(status().isNoContent());

            // 確認：状態が「販売終了」
            products.getProduct(id).andExpect(jsonPath("$.status").value("DISCONTINUED"));
        }

        @Test
        @DisplayName("販売終了の商品を販売終了にしても 204")
        void discontinuesIdempotently() throws Exception {
            // 準備：販売終了の商品
            var id = discontinuedProduct();

            // 操作と確認：受け付けられる
            products.discontinue(id).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("AC-32 販売終了の商品は公開に戻せず 400")
        void rejectsPublishing() throws Exception {
            // 準備：販売終了の商品
            var id = discontinuedProduct();

            // 操作と確認：販売終了なので拒否される
            products.publish(id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PRODUCT_DISCONTINUED"));
        }

        @Test
        @DisplayName("AC-32 販売終了の商品は非公開にもできず 400")
        void rejectsUnpublishing() throws Exception {
            // 準備：販売終了の商品
            var id = discontinuedProduct();

            // 操作と確認：販売終了なので拒否される
            products.unpublish(id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PRODUCT_DISCONTINUED"));
        }
    }

    @Nested
    @DisplayName("商品の削除")
    class Delete {

        @Test
        @DisplayName("AC-33 C 側に一度も表示されていない商品は削除できる")
        void deletesNeverShownProduct() throws Exception {
            // 準備：公開したことのない商品（バージョン 1）
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作：削除する
            products.deleteProduct(id, 1).andExpect(status().isNoContent());

            // 確認：取得できない
            products.getProduct(id).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-33 表示されたことのある商品は削除できず 400")
        void rejectsDeletingShownProduct() throws Exception {
            // 準備：公開した後に販売終了にした商品（バージョン 3）
            var id = discontinuedProduct();

            // 操作と確認：表示されたことがあるので拒否される
            products.deleteProduct(id, 3)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PRODUCT_ALREADY_SHOWN"));
        }

        @Test
        @DisplayName("AC-33 公開を予約し、開始日時の前に取り消した商品は削除できる")
        void deletesProductWithCanceledReservation() throws Exception {
            // 準備：開始日時を未来にして公開し、開始日時の前に非公開にした商品（バージョン 4）
            var id = products.productWithVariant(PRODUCT_CODE);
            products.setPeriod(id, quoted(PUBLISH_START), "null", 1).andExpect(status().isNoContent());
            products.publish(id).andExpect(status().isNoContent());
            products.unpublish(id).andExpect(status().isNoContent());

            // 操作と確認：表示されていないものとして、削除できる
            products.deleteProduct(id, 4).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("AC-269 削除できる商品は、削除できない理由を返さない")
        void omitsDeleteBlockedReason() throws Exception {
            // 準備：公開したことのない商品
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作と確認：削除でき、理由がない
            products.getProduct(id)
                    .andExpect(jsonPath("$.actions.delete").value(true))
                    .andExpect(jsonPath("$.actions.deleteBlockedReason").doesNotExist());
        }

        @Test
        @DisplayName("AC-269 削除できない商品は、削除できない理由（C 側に表示されたこと）を返す")
        void returnsDeleteBlockedReason() throws Exception {
            // 準備：公開中の商品
            var id = publishedProduct();

            // 操作と確認：削除できず、理由が「表示されたことがある」
            products.getProduct(id)
                    .andExpect(jsonPath("$.actions.delete").value(false))
                    .andExpect(jsonPath("$.actions.deleteBlockedReason").value("ALREADY_SHOWN"));
        }

        @Test
        @DisplayName("古いバージョンで削除すると 409")
        void rejectsVersionMismatch() throws Exception {
            // 準備：バージョンが 1 の商品
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作と確認：古いバージョン 0 での削除は、競合で拒否される
            products.deleteProduct(id, 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }
    }

    @Nested
    @DisplayName("公開期間")
    class PublicationPeriod {

        @Test
        @DisplayName("AC-34 公開開始日時と公開終了日時を設定でき、詳細に出る")
        void setsPeriod() throws Exception {
            // 準備：バージョンが 1 の商品
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作：開始と終了の日時を設定する
            products.setPeriod(id, quoted(PUBLISH_START), quoted(PUBLISH_END), 1).andExpect(status().isNoContent());

            // 確認：詳細に、設定した開始と終了の日時がある
            products.getProduct(id)
                    .andExpect(jsonPath("$.publishStart").value(PUBLISH_START_UTC))
                    .andExpect(jsonPath("$.publishEnd").value(PUBLISH_END_UTC));
        }

        @Test
        @DisplayName("AC-34 公開終了日時だけを設定できる")
        void setsEndOnly() throws Exception {
            // 準備：バージョンが 1 の商品
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作：開始日時を空にして、終了日時だけを設定する
            products.setPeriod(id, "null", quoted(PUBLISH_END), 1).andExpect(status().isNoContent());

            // 確認：詳細に、終了日時だけがある
            products.getProduct(id)
                    .andExpect(jsonPath("$.publishStart").doesNotExist())
                    .andExpect(jsonPath("$.publishEnd").value(PUBLISH_END_UTC));
        }

        @Test
        @DisplayName("AC-34 公開期間は空にできる")
        void clearsPeriod() throws Exception {
            // 準備：開始と終了の日時を設定した商品（バージョン 2）
            var id = products.productWithVariant(PRODUCT_CODE);
            products.setPeriod(id, quoted(PUBLISH_START), quoted(PUBLISH_END), 1).andExpect(status().isNoContent());

            // 操作：開始と終了の日時を空にする
            products.setPeriod(id, "null", "null", 2).andExpect(status().isNoContent());

            // 確認：詳細に、開始と終了の日時がない
            products.getProduct(id)
                    .andExpect(jsonPath("$.publishStart").doesNotExist())
                    .andExpect(jsonPath("$.publishEnd").doesNotExist());
        }

        @Test
        @DisplayName("公開期間を設定すると、バージョンが上がる")
        void incrementsVersion() throws Exception {
            // 準備：バージョンが 1 の商品
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作：公開期間を設定する
            products.setPeriod(id, quoted(PUBLISH_START), quoted(PUBLISH_END), 1).andExpect(status().isNoContent());

            // 確認：バージョンが 2
            products.getProduct(id).andExpect(jsonPath("$.version").value(2));
        }

        @Test
        @DisplayName("開始日時が未来の商品を公開すると、初回表示日時は開始日時になる")
        void reschedulesFirstShownAt() throws Exception {
            // 準備：開始日時を未来にした商品
            var id = products.productWithVariant(PRODUCT_CODE);
            products.setPeriod(id, quoted(PUBLISH_START), "null", 1).andExpect(status().isNoContent());

            // 操作：公開する
            products.publish(id).andExpect(status().isNoContent());

            // 確認：初回表示日時が開始日時
            products.getProduct(id).andExpect(jsonPath("$.firstShownAt").value(PUBLISH_START_UTC));
        }

        @Test
        @DisplayName("AC-36 終了日時が開始日時より後でなければ 400")
        void rejectsEndNotAfterStart() throws Exception {
            // 準備：商品
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作と確認：開始と同じ終了日時は、公開期間の誤りで拒否される
            products.setPeriod(id, quoted(PUBLISH_START), quoted(PUBLISH_START), 1)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PUBLICATION_PERIOD"));
        }

        @Test
        @DisplayName("取得時のバージョンと一致しなければ 409")
        void rejectsVersionMismatch() throws Exception {
            // 準備：バージョンが 1 の商品
            var id = products.productWithVariant(PRODUCT_CODE);

            // 操作と確認：古いバージョン 0 での設定は、競合で拒否される
            products.setPeriod(id, "null", "null", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("存在しない商品は 404")
        void rejectsUnknownProduct() throws Exception {
            // 操作と確認：見つからない
            products.setPeriod(UNKNOWN_ID, "null", "null", 0).andExpect(status().isNotFound());
        }
    }

    // ============================== 準備の補助 ==============================

    /**
     * バリアントを持つ商品を公開する（バージョン 2）。
     *
     * @return 商品 ID
     */
    private UUID publishedProduct() throws Exception {
        var id = products.productWithVariant(PRODUCT_CODE);
        products.publish(id).andExpect(status().isNoContent());
        return id;
    }

    /**
     * バリアントを持つ商品を公開した後に、販売終了にする（バージョン 3）。
     *
     * @return 商品 ID
     */
    private UUID discontinuedProduct() throws Exception {
        var id = publishedProduct();
        products.discontinue(id).andExpect(status().isNoContent());
        return id;
    }

    /**
     * 日時を JSON の文字列にする。
     *
     * @param value 日時
     * @return JSON の文字列の値
     */
    private static String quoted(String value) {
        return "\"" + value + "\"";
    }
}
