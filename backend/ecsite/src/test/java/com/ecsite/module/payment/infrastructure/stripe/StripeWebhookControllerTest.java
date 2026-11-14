package com.ecsite.module.payment.infrastructure.stripe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stripe の通知のエンドポイントの単体テスト
 *
 * <p>署名の確認は受け取り（{@link StripeNotificationHandler}）のテストで確認し、
 * ここでは本文と署名を受け取りに渡すことと、確認できないときに 400 を返すことを確認する。
 */
class StripeWebhookControllerTest {

    // ============================== 定数 ==============================

    /**
     * 通知の本文
     */
    private static final String PAYLOAD = """
            {"id": "evt_test", "type": "checkout.session.completed"}""";

    /**
     * 署名のヘッダーの値
     */
    private static final String SIGNATURE = "t=1,v1=signature";

    // ============================== 対象 ==============================

    /**
     * 通知の受け取り
     */
    private final StripeNotificationHandler handler = mock(StripeNotificationHandler.class);

    /**
     * エンドポイントを呼ぶ MockMvc
     */
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StripeWebhookController(handler)).build();
    }

    // ============================== テスト ==============================

    @Test
    @DisplayName("通知の本文と署名を、文字列のまま受け取りに渡し、200 を返す")
    void passesPayloadAndSignature() throws Exception {
        // 操作：署名付きの通知を受ける
        var result = receive(SIGNATURE);

        // 確認：200 で、本文と署名をそのまま渡した
        result.andExpect(status().isOk());
        verify(handler).handle(PAYLOAD, SIGNATURE);
    }

    @Test
    @DisplayName("署名を確認できなければ、400 を返す")
    void rejectsInvalidSignature() throws Exception {
        // 準備：受け取りが署名を確認できない
        doThrow(new InvalidStripeNotificationException("署名が合わない", null)).when(handler).handle(anyString(), anyString());

        // 操作：通知を受ける
        var result = receive(SIGNATURE);

        // 確認：400
        result.andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("署名のヘッダーがなくても受け取りに渡し、受け取りが確認する")
    void passesMissingSignature() throws Exception {
        // 操作：署名のヘッダーのない通知を受ける
        var result = mockMvc.perform(post("/api/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD));

        // 確認：200 で、署名なしとして渡した
        result.andExpect(status().isOk());
        verify(handler).handle(PAYLOAD, null);
    }

    // ============================== 準備の補助 ==============================

    /**
     * 署名付きの通知を受ける。
     *
     * @param signature 署名のヘッダーの値
     * @return 応答
     * @throws Exception 呼び出しに失敗したとき
     */
    private ResultActions receive(String signature) throws Exception {
        return mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .content(PAYLOAD));
    }
}
