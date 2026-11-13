package com.ecsite;

import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRegistry;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Predicate;

import static org.awaitility.Awaitility.await;

/**
 * 非同期の処理の待ち
 *
 * <p>非同期の処理（コミットの後に動くイベントのリスナー）を待つ。統合テストの土台（{@link IntegrationTest}）が登録する。
 *
 * <p>「起きないこと」は、決まった時間だけ見るのでなく、リスナーが終わったことを確認してから、起きた数を確認する。
 * 決まった時間だけ見ると、遅い環境ではリスナーが動く前に見終わり、誤って通るため。
 *
 * <p>イベントの発行の記録は、発行したトランザクションのコミットまでに書かれる。
 * コミットした後に呼べば、そのトランザクションで発行したイベントをすべて待てる。
 */
public class AsyncEvents {

    /**
     * 非同期の処理を待つ時間の上限
     *
     * <p>長く待つ必要があるテストだけ、理由を書いて別の値を使う。
     */
    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * 再発行のジョブが対象にする、発行からの時間
     */
    private static final Duration RESUBMIT_AFTER = Duration.ofMinutes(5);

    /**
     * イベントの発行の記録
     */
    private final EventPublicationRegistry registry;

    /**
     * 未完了のイベントの発行の記録
     */
    private final IncompleteEventPublications incompletePublications;

    /**
     * アプリの時計
     */
    private final Clock clock;

    /**
     * @param registry               イベントの発行の記録
     * @param incompletePublications 未完了のイベントの発行の記録
     * @param clock                  アプリの時計
     */
    public AsyncEvents(EventPublicationRegistry registry, IncompleteEventPublications incompletePublications, Clock clock) {
        this.registry = registry;
        this.incompletePublications = incompletePublications;
        this.clock = clock;
    }

    /**
     * 条件に合う未完了のイベントを、再発行のジョブと同じく再発行する。
     *
     * <p>対象は、発行から {@link #RESUBMIT_AFTER} を過ぎた記録のうち、条件に合うイベントだけ。
     * 統合テストは 1 つのコンテキストで DB を共有するので、全件を再発行すると、他のテストが残した失敗も処理される。
     * 再発行の後のリスナーの処理は待たない。
     *
     * @param event 再発行するイベントの条件（テストで作った ID などで、このテストのイベントだけに絞る）
     */
    public void resubmit(Predicate<Object> event) {
        var before = clock.instant().minus(RESUBMIT_AFTER);
        incompletePublications.resubmitIncompletePublications(publication ->
                publication.getPublicationDate().isBefore(before) && event.test(publication.getEvent()));
    }

    /**
     * 条件に合うイベントのリスナーが、すべて終わるまで待つ。
     *
     * <p>条件に合うイベントがなければ、すぐに戻る。
     *
     * <p>リスナーが失敗したイベントは終わらないので、失敗を確認するテストでは {@link #awaitFailed} を使う。
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

    /**
     * 条件に合うイベントのリスナーが、指定の回数以上処理して失敗した状態になるまで待つ（再発行の後の失敗を待つ）。
     *
     * @param event    待つイベントの条件（テストで作った ID などで、このテストのイベントだけに絞る）
     * @param attempts 処理の回数
     */
    public void awaitFailed(Predicate<Object> event, int attempts) {
        await().atMost(TIMEOUT).until(() -> registry.findIncompletePublications().stream()
                .anyMatch(publication -> event.test(publication.getEvent())
                        && publication.getStatus() == EventPublication.Status.FAILED
                        && publication.getCompletionAttempts() >= attempts));
    }
}
