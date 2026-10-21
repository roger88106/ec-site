package com.ecsite.module.payment.infrastructure.mock;

import com.ecsite.common.model.value.Money;
import com.ecsite.module.payment.application.port.PaymentGateway.PageRequest;
import com.ecsite.module.payment.application.port.PaymentNotificationReceiver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
 * mock の支払画面の単体テスト。画面の表示、支払う・カードを拒否させる・やめるの操作と、戻る先を確認する。
 */
class MockPaymentPageControllerTest {

    private static final String RETURN_URL = "http://localhost:3000/orders/1?payment=returned";

    private final PaymentNotificationReceiver receiver = mock(PaymentNotificationReceiver.class);

    private final MockPaymentGateway gateway = new MockPaymentGateway(receiver, new MockPaymentProperties("http://localhost:8080"));

    private MockMvc mockMvc;

    private String pageId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MockPaymentPageController(gateway)).build();
        pageId = gateway.openPage(new PageRequest(UUID.randomUUID(), UUID.randomUUID(), Money.of(6940), RETURN_URL)).id();
    }

    @Test
    @DisplayName("開いている画面は、金額と 3 つの操作を出す")
    void showsOpenPage() throws Exception {
        mockMvc.perform(get("/mock-payment/" + pageId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("お支払い金額：6,940 円")))
                .andExpect(content().string(containsString(">支払う</button>")))
                .andExpect(content().string(containsString(">カードを拒否させる</button>")))
                .andExpect(content().string(containsString(">やめる</button>")));
    }

    @Test
    @DisplayName("知らない画面は、404 で「見つかりません」と出す")
    void unknownPage() throws Exception {
        mockMvc.perform(get("/mock-payment/mock_page_unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("支払の画面が見つかりません。")));
    }

    @Test
    @DisplayName("支払うと、与信の成功を通知し、店舗の注文の画面に戻す（303）")
    void pays() throws Exception {
        mockMvc.perform(post("/mock-payment/" + pageId + "/pay"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", RETURN_URL));

        assertThat(gateway.page(pageId).orElseThrow().status()).isEqualTo(MockPaymentGateway.PageStatus.PAID);
        verify(receiver).authorized(eq(pageId), any());
    }

    @Test
    @DisplayName("支払済みの画面では、支払い直さず、「支払は済んでいます」と出す")
    void doesNotPayTwice() throws Exception {
        gateway.markPaid(pageId);

        mockMvc.perform(post("/mock-payment/" + pageId + "/pay"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("支払は済んでいます。")));
        verify(receiver, never()).authorized(any(), any());
    }

    @Test
    @DisplayName("閉じた画面では、「支払の期限が切れました」と出し、支払わない")
    void closedPage() throws Exception {
        gateway.closePage(pageId);

        mockMvc.perform(post("/mock-payment/" + pageId + "/pay"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("支払の期限が切れました。")));
        verify(receiver, never()).authorized(any(), any());
    }

    @Test
    @DisplayName("カードを拒否させると、状態を変えずに拒否を示して同じ画面を出す")
    void declines() throws Exception {
        mockMvc.perform(post("/mock-payment/" + pageId + "/decline"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("カードが拒否されました。")))
                .andExpect(content().string(containsString(">支払う</button>")));

        assertThat(gateway.page(pageId).orElseThrow().status()).isEqualTo(MockPaymentGateway.PageStatus.OPEN);
    }

    @Test
    @DisplayName("やめると、支払わずに店舗の注文の画面に戻す（303）")
    void cancels() throws Exception {
        mockMvc.perform(post("/mock-payment/" + pageId + "/cancel"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", RETURN_URL));

        assertThat(gateway.page(pageId).orElseThrow().status()).isEqualTo(MockPaymentGateway.PageStatus.OPEN);
        verify(receiver, never()).authorized(any(), any());
    }

    @Test
    @DisplayName("戻る先の URL は、画面に出すときに HTML の記号を逃がす")
    void escapesReturnUrl() throws Exception {
        var unsafe = gateway.openPage(new PageRequest(UUID.randomUUID(), UUID.randomUUID(), Money.of(100),
                "http://localhost:3000/orders/1?a=\"<b>")).id();
        gateway.closePage(unsafe);

        mockMvc.perform(get("/mock-payment/" + unsafe))
                .andExpect(content().string(containsString("&quot;&lt;b&gt;")))
                .andExpect(content().string(not(containsString("\"<b>"))));
    }
}
