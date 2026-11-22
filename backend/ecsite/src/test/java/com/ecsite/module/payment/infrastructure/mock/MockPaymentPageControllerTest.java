package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.common.model.id.OrderId;
import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.port.PaymentGateway.PageRequest;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import com.ecsite.module.payment.domain.payment.PaymentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * mock の支払画面の単体テスト
 *
 * <p>画面の表示、支払う・カードを拒否させる・やめるの操作と、戻る先を確認する。
 */
class MockPaymentPageControllerTest {

    // ============================== 定数 ==============================

    /**
     * 支払画面から戻る店舗の注文の画面
     */
    private static final String RETURN_URL = "http://localhost:3000/orders/1?payment=returned";

    /**
     * 支払の金額
     */
    private static final Money AMOUNT = Money.of(6940);

    // ============================== 対象 ==============================

    /**
     * 与信の成功の通知を受ける port
     */
    private final PaymentNotificationReceiver receiver = mock(PaymentNotificationReceiver.class);

    /**
     * 画面の状態を持つ本番の mock
     */
    private final MockPaymentGateway gateway = new MockPaymentGateway(receiver, new MockPaymentProperties("http://localhost:8080"),
            Clock.systemUTC(), UUID::randomUUID);

    /**
     * 画面を呼ぶ MockMvc
     */
    private MockMvc mockMvc;

    /**
     * 開いている支払画面の ID
     */
    private String pageId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MockPaymentPageController(gateway)).build();
        pageId = gateway.openPage(new PageRequest(new PaymentId(UUID.randomUUID()), new OrderId(UUID.randomUUID()), AMOUNT, RETURN_URL)).id();
    }

    // ============================== テスト ==============================

    @Nested
    @DisplayName("画面の表示")
    class Show {

        @Test
        @DisplayName("開いている画面は、金額と 3 つの操作を出す")
        void showsOpenPage() throws Exception {
            // 操作：開いている画面を表示する
            var result = mockMvc.perform(get("/mock-payment/" + pageId));

            // 確認：金額と、支払う・カードを拒否させる・やめるのボタン
            assertOpenPageShown(result);
        }

        @Test
        @DisplayName("知らない画面は、404 で「見つかりません」と出す")
        void unknownPage() throws Exception {
            // 操作：知らない画面を表示する
            var result = mockMvc.perform(get("/mock-payment/mock_page_unknown"));

            // 確認：404 と「見つかりません」
            result.andExpect(status().isNotFound())
                    .andExpect(content().string(containsString("支払の画面が見つかりません。")));
        }

        @Test
        @DisplayName("戻る先の URL は、画面に出すときに HTML の記号を逃がす")
        void escapesReturnUrl() throws Exception {
            // 準備：HTML の記号を含む戻る先の、閉じた画面（戻る先を画面に出す）
            var unsafe = gateway.openPage(new PageRequest(new PaymentId(UUID.randomUUID()), new OrderId(UUID.randomUUID()), Money.of(100),
                    "http://localhost:3000/orders/1?a=\"<b>")).id();
            gateway.closePage(unsafe);

            // 操作：画面を表示する
            var result = mockMvc.perform(get("/mock-payment/" + unsafe));

            // 確認：記号が逃がしてあり、そのままでは出ない
            result.andExpect(content().string(containsString("&quot;&lt;b&gt;")))
                    .andExpect(content().string(not(containsString("\"<b>"))));
        }
    }

    @Nested
    @DisplayName("支払う")
    class Pay {

        @Test
        @DisplayName("支払うと、店舗の注文の画面に戻す（303）")
        void redirectsToStore() throws Exception {
            // 操作：支払う
            var result = mockMvc.perform(post("/mock-payment/" + pageId + "/pay"));

            // 確認：303 で戻る先へ
            result.andExpect(status().isSeeOther())
                    .andExpect(header().string("Location", RETURN_URL));
        }

        @Test
        @DisplayName("支払うと、画面は支払済みになり、与信の成功を通知する")
        void paysAndNotifies() throws Exception {
            // 操作：支払う
            mockMvc.perform(post("/mock-payment/" + pageId + "/pay"));

            // 確認：支払済みで、この画面の与信の成功を通知した
            assertThat(gateway.page(pageId).orElseThrow().status()).isEqualTo(MockPaymentGateway.PageStatus.PAID);
            verify(receiver).authorized(eq(pageId), any());
        }

        @Test
        @DisplayName("支払済みの画面では、支払い直さず、「支払は済んでいます」と出す")
        void doesNotPayTwice() throws Exception {
            // 準備：支払の済んだ画面
            gateway.markPaid(pageId);

            // 操作：支払う
            var result = mockMvc.perform(post("/mock-payment/" + pageId + "/pay"));

            // 確認：「支払は済んでいます」と出し、通知しない
            result.andExpect(status().isOk())
                    .andExpect(content().string(containsString("支払は済んでいます。")));
            verify(receiver, never()).authorized(any(), any());
        }

        @Test
        @DisplayName("閉じた画面では、「支払の期限が切れました」と出し、支払わない")
        void closedPage() throws Exception {
            // 準備：閉じた画面
            gateway.closePage(pageId);

            // 操作：支払う
            var result = mockMvc.perform(post("/mock-payment/" + pageId + "/pay"));

            // 確認：「支払の期限が切れました」と出し、通知しない
            result.andExpect(status().isOk())
                    .andExpect(content().string(containsString("支払の期限が切れました。")));
            verify(receiver, never()).authorized(any(), any());
        }
    }

    @Nested
    @DisplayName("カードを拒否させる・やめる")
    class DeclineAndCancel {

        @Test
        @DisplayName("カードを拒否させると、拒否を示して同じ画面を出す")
        void declines() throws Exception {
            // 操作：カードを拒否させる
            var result = mockMvc.perform(post("/mock-payment/" + pageId + "/decline"));

            // 確認：拒否の表示と、支払うボタン
            result.andExpect(status().isOk())
                    .andExpect(content().string(containsString("カードが拒否されました。")))
                    .andExpect(content().string(containsString(">支払う</button>")));
        }

        @Test
        @DisplayName("カードを拒否させても、画面は開いたまま")
        void keepsOpenOnDecline() throws Exception {
            // 操作：カードを拒否させる
            mockMvc.perform(post("/mock-payment/" + pageId + "/decline"));

            // 確認：開いたまま
            assertThat(gateway.page(pageId).orElseThrow().status()).isEqualTo(MockPaymentGateway.PageStatus.OPEN);
        }

        @Test
        @DisplayName("やめると、店舗の注文の画面に戻す（303）")
        void cancels() throws Exception {
            // 操作：やめる
            var result = mockMvc.perform(post("/mock-payment/" + pageId + "/cancel"));

            // 確認：303 で戻る先へ
            result.andExpect(status().isSeeOther())
                    .andExpect(header().string("Location", RETURN_URL));
        }

        @Test
        @DisplayName("やめても、支払わず、画面は開いたまま")
        void keepsOpenOnCancel() throws Exception {
            // 操作：やめる
            mockMvc.perform(post("/mock-payment/" + pageId + "/cancel"));

            // 確認：開いたままで、通知しない
            assertThat(gateway.page(pageId).orElseThrow().status()).isEqualTo(MockPaymentGateway.PageStatus.OPEN);
            verify(receiver, never()).authorized(any(), any());
        }
    }

    // ============================== 確認の補助 ==============================

    /**
     * 開いている画面が出ていることを確認する。
     *
     * <p>確認すること：
     * <ul>
     *   <li>200</li>
     *   <li>金額（{@link #AMOUNT} の 6,940 円）</li>
     *   <li>支払うボタン</li>
     *   <li>カードを拒否させるボタン</li>
     *   <li>やめるボタン</li>
     * </ul>
     *
     * @param result 画面の応答
     * @throws Exception 応答を読めないとき
     */
    private static void assertOpenPageShown(ResultActions result) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(content().string(containsString("お支払い金額：6,940 円")))
                .andExpect(content().string(containsString(">支払う</button>")))
                .andExpect(content().string(containsString(">カードを拒否させる</button>")))
                .andExpect(content().string(containsString(">やめる</button>")));
    }
}
