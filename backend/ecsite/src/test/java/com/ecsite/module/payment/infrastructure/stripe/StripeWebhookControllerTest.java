package com.ecsite.module.payment.infrastructure.stripe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stripe の通知のエンドポイントの単体テスト。署名の確認は受け取り（{@link StripeNotificationHandler}）のテストで確認し、
 * ここでは本文と署名を受け取りに渡すことと、確認できないときに 400 を返すことを確認する。
 */
class StripeWebhookControllerTest {

    private static final String PAYLOAD = """
            {"id": "evt_test", "type": "checkout.session.completed"}""";

    private static final String SIGNATURE = "t=1,v1=signature";

    private final StripeNotificationHandler handler = mock(StripeNotificationHandler.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StripeWebhookController(handler)).build();
    }

    @Test
    @DisplayName("通知の本文と署名を、文字列のまま受け取りに渡し、200 を返す")
    void passesPayloadAndSignature() throws Exception {
        receive(SIGNATURE).andExpect(status().isOk());

        verify(handler).handle(PAYLOAD, SIGNATURE);
    }

    @Test
    @DisplayName("署名を確認できなければ、400 を返す")
    void rejectsInvalidSignature() throws Exception {
        doThrow(new InvalidStripeNotificationException("署名が合わない", null)).when(handler).handle(anyString(), anyString());

        receive(SIGNATURE).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("署名のヘッダーがなくても受け取りに渡し、受け取りが確認する")
    void passesMissingSignature() throws Exception {
        mockMvc.perform(post("/api/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk());

        verify(handler).handle(PAYLOAD, null);
    }

    private org.springframework.test.web.servlet.ResultActions receive(String signature) throws Exception {
        return mockMvc.perform(post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .content(PAYLOAD));
    }
}
