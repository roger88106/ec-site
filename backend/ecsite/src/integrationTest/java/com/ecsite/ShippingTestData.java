package com.ecsite;

import com.ecsite.module.shipping.domain.Carrier;
import com.ecsite.module.shipping.infrastructure.mock.MockCarrier;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 統合テストで、B 側の API で出荷を登録し、mock の物流サービスから配送状態の通知を送る補助。<br />
 * 注文は、呼ぶ側が先に用意する。
 */
public class ShippingTestData {

    /**
     * MockMvc
     */
    private final MockMvc mockMvc;

    /**
     * mock の物流サービス
     */
    private final MockCarrier mockCarrier;

    /**
     * コンストラクタ
     *
     * @param mockMvc     MockMvc
     * @param mockCarrier mock の物流サービス
     */
    public ShippingTestData(MockMvc mockMvc, MockCarrier mockCarrier) {
        this.mockMvc = mockMvc;
        this.mockCarrier = mockCarrier;
    }

    /**
     * 他のテストが確定させた出荷と重ならない送り状番号（12 桁）を作る。
     *
     * @return 送り状番号
     */
    public static String trackingNumber() {
        return String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000_000_000L) + 100_000_000_000L);
    }

    /**
     * 出荷を登録し、201 を確認する。
     *
     * @param orderId        注文 ID
     * @param carrier        物流サービス
     * @param trackingNumber 送り状番号
     */
    public void ship(UUID orderId, Carrier carrier, String trackingNumber) throws Exception {
        mockMvc.perform(post("/api/admin/orders/" + orderId + "/shipment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"carrier": "%s", "trackingNumber": "%s"}""".formatted(carrier.name(), trackingNumber)))
                .andExpect(status().isCreated());
    }

    /**
     * mock の物流サービスから、署名付きの配送状態の通知をエンドポイントへ送り、200 を確認する。
     *
     * @param carrier        物流サービス
     * @param trackingNumber 送り状番号
     * @param statusName     配送状態の名前
     */
    public void notifyStatus(Carrier carrier, String trackingNumber, String statusName) throws Exception {
        var notification = mockCarrier.notification(carrier, trackingNumber, statusName);
        mockMvc.perform(post("/api/webhooks/carrier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Carrier-Signature", notification.signature())
                        .content(notification.body()))
                .andExpect(status().isOk());
    }
}
