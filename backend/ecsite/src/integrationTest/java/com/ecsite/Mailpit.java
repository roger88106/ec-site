package com.ecsite;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

/**
 * 統合テストのメールの受け手（Mailpit のコンテナ）。docker/compose.yaml と同じイメージを使う。<br />
 * SMTP で受けたメールを、Mailpit の API で読む。テストは宛先を毎回変えて、他のテストのメールと混ぜない。
 */
public class Mailpit implements AutoCloseable {

    /**
     * SMTP のポート
     */
    private static final int SMTP_PORT = 1025;

    /**
     * API と画面のポート
     */
    private static final int HTTP_PORT = 8025;

    /**
     * コンテナ
     */
    private final GenericContainer<?> container;

    /**
     * Mailpit の API
     */
    private final RestClient api;

    /**
     * コンテナを起動する。
     */
    public Mailpit() {
        container = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.6"))
                .withExposedPorts(SMTP_PORT, HTTP_PORT)
                .waitingFor(Wait.forHttp("/livez").forPort(HTTP_PORT));
        container.start();
        api = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://" + container.getHost() + ":" + container.getMappedPort(HTTP_PORT) + "/api/v1")
                .build();
    }

    /**
     * @return テストの JVM から見た SMTP のホスト
     */
    public String smtpHost() {
        return container.getHost();
    }

    /**
     * @return テストの JVM から見た SMTP のポート（割り当てられたポート）
     */
    public int smtpPort() {
        return container.getMappedPort(SMTP_PORT);
    }

    /**
     * 宛先に届いたメールを、届いた順に返す。
     *
     * @param address 宛先のメールアドレス
     * @return 届いたメール
     */
    @SuppressWarnings("unchecked")
    public List<Message> messagesTo(String address) {
        Map<String, Object> result = api.get()
                .uri(uri -> uri.path("/search").queryParam("query", "to:\"" + address + "\"").build())
                .retrieve()
                .body(Map.class);
        var messages = (List<Map<String, Object>>) result.get("messages");
        return messages.reversed().stream()
                .map(summary -> {
                    Map<String, Object> message = api.get().uri("/message/{id}", summary.get("ID")).retrieve().body(Map.class);
                    var from = (Map<String, Object>) message.get("From");
                    return new Message((String) from.get("Address"), (String) message.get("Subject"), (String) message.get("Text"));
                })
                .toList();
    }

    @Override
    public void close() {
        container.stop();
    }

    /**
     * 届いたメール
     *
     * @param from    送信元
     * @param subject 件名
     * @param text    本文（テキスト）
     */
    public record Message(String from, String subject, String text) {
    }
}
