package com.ecsite.module.notification.application;

import com.ecsite.AsyncEvents;
import com.ecsite.IntegrationTest;
import com.ecsite.module.notification.application.port.Mailer;
import com.ecsite.module.order.event.OrderShipped;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ecsite.jooq.notification.Tables.SENT_MAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * SMTP サーバーが応えないときの統合テスト。<br />
 * 接続を受けても何も返さない SMTP サーバーに向け、送信が待ち時間の上限で失敗し（止まったままにならない）、
 * 送ったメールの記録が巻き戻ることを確認する。失敗したイベントは、未完了のイベントの再発行で送り直される。
 */
@IntegrationTest
@DirtiesContext
class SmtpTimeoutTest {

    /**
     * 接続を受けるが、何も返さない SMTP サーバー
     */
    private static final SilentServer SILENT_SMTP = new SilentServer();

    /**
     * 送信の失敗を待つ時間の上限（設定の待ち時間の上限に、余裕を加える）
     */
    private static final Duration SEND_FAILURE_TIMEOUT = Duration.ofSeconds(40);

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    DSLContext dsl;

    @Autowired
    JavaMailSenderImpl mailSender;

    @MockitoSpyBean
    Mailer mailer;

    /**
     * 送信の部品の宛先を、応えないサーバーにする（待ち時間の上限などの設定は、アプリの設定のまま）。
     * 部品を書き換えるので、このテストの後に Spring のコンテキストを捨てる。
     */
    @BeforeEach
    void pointToSilentServer() {
        mailSender.setHost("localhost");
        mailSender.setPort(SILENT_SMTP.port());
    }

    @AfterAll
    static void stop() {
        SILENT_SMTP.close();
    }

    @Test
    @DisplayName("T9406 SMTP サーバーが応えなければ、送信は待ち時間の上限で失敗し、送ったメールの記録は残らない")
    void failsWithinTimeout() {
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            try {
                return invocation.callRealMethod();
            } catch (RuntimeException e) {
                failures.add(e);
                throw e;
            }
        }).when(mailer).send(any());
        var orderId = UUID.randomUUID();

        transaction.executeWithoutResult(status -> events.publishEvent(OrderShipped.of(Instant.now(), orderId,
                "20261008-000001", "timeout-" + orderId + "@example.com", OrderShipped.Carrier.YAMATO, "123456789012")));

        await().atMost(SEND_FAILURE_TIMEOUT).until(() -> !failures.isEmpty());
        await().atMost(AsyncEvents.TIMEOUT).until(() -> dsl.fetchCount(SENT_MAIL, SENT_MAIL.ORDER_ID.eq(orderId)) == 0);
        assertThat(SILENT_SMTP.accepted()).isPositive();
    }

    /**
     * 接続を受けて、何も返さずに持ち続けるサーバー
     */
    private static final class SilentServer implements AutoCloseable {

        private final ServerSocket socket;

        private final List<Socket> connections = new CopyOnWriteArrayList<>();

        SilentServer() {
            try {
                socket = new ServerSocket(0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Thread.ofVirtual().start(() -> {
                while (!socket.isClosed()) {
                    try {
                        connections.add(socket.accept());
                    } catch (IOException e) {
                        return;
                    }
                }
            });
        }

        int port() {
            return socket.getLocalPort();
        }

        int accepted() {
            return connections.size();
        }

        @Override
        public void close() {
            try {
                socket.close();
                for (var connection : connections) {
                    connection.close();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
