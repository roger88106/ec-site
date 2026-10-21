package com.ecsite;

import java.time.Duration;
import java.util.function.Predicate;

import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.core.EventPublicationRegistry;

import static org.awaitility.Awaitility.await;

/**
 * 非同期の処理（コミットの後に動くイベントのリスナー）を待つ補助。統合テストの土台（{@link IntegrationTest}）が登録する。<br />
 * 「起きないこと」は、決まった時間だけ見るのでなく、リスナーが終わったことを確認してから、起きた数を確認する。
 * 決まった時間だけ見ると、遅い環境ではリスナーが動く前に見終わり、誤って通るため。<br />
 * イベントの発行の記録は、発行したトランザクションのコミットまでに書かれる。
 * コミットした後に呼べば、そのトランザクションで発行したイベントをすべて待てる。
 */
public class AsyncEvents {

    /**
     * 非同期の処理を待つ時間の上限。長く待つ必要があるテストだけ、理由を書いて別の値を使う
     */
    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * イベントの発行の記録
     */
    private final EventPublicationRegistry registry;

    /**
     * @param registry イベントの発行の記録
     */
    public AsyncEvents(EventPublicationRegistry registry) {
        this.registry = registry;
    }

    /**
     * 条件に合うイベントのリスナーが、すべて終わるまで待つ。条件に合うイベントがなければ、すぐに戻る。<br />
     * リスナーが失敗したイベントは終わらないので、失敗を確認するテストでは {@link #awaitFailed} を使う。
     * リスナーの中で発行されるイベントは、呼んだ時にまだないことがあるので、先に発行した側の結果を待ってから呼ぶ。
     *
     * @param event 待つイベントの条件（テストで作った ID などで、このテストのイベントだけに絞る）
     */
    public void awaitHandled(Predicate<Object> event) {
        await().atMost(TIMEOUT).until(() -> registry.findIncompletePublications().stream()
                .noneMatch(publication -> event.test(publication.getEvent())));
    }

    /**
     * 条件に合うイベントのリスナーが、失敗して終わるまで待つ（失敗したイベントは、未完了のまま再発行を待つ）。
     *
     * @param event 待つイベントの条件（テストで作った ID などで、このテストのイベントだけに絞る）
     */
    public void awaitFailed(Predicate<Object> event) {
        await().atMost(TIMEOUT).until(() -> registry.findIncompletePublications().stream()
                .anyMatch(publication -> event.test(publication.getEvent())
                        && publication.getStatus() == EventPublication.Status.FAILED));
    }
}
