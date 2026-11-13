package com.ecsite;

import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRegistry;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
     * 完了したイベントの発行の記録
     */
    private final CompletedEventPublications completedPublications;

    /**
     * アプリの時計
     */
    private final Clock clock;

    /**
     * 待つ対象のイベントの発行の記録と時計を受け取る。
     *
     * @param registry               イベントの発行の記録
     * @param incompletePublications 未完了のイベントの発行の記録
     * @param completedPublications  完了したイベントの発行の記録
     * @param clock                  アプリの時計
     */
    public AsyncEvents(EventPublicationRegistry registry, IncompleteEventPublications incompletePublications,
                       CompletedEventPublications completedPublications, Clock clock) {
        this.registry = registry;
        this.incompletePublications = incompletePublications;
        this.completedPublications = completedPublications;
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
     * 条件に合うイベントの発行の記録が 1 件以上あることを確認してから、そのリスナーが全て終わるまで待つ。
     *
     * <p>{@link #awaitHandled} は、条件に合うイベントがなければすぐに戻るので、条件の誤りやイベントの発行の漏れでも通る。
     * 「起きないこと」を確認するテストで、発行されたイベントを待ったことを保証するために使う。
     * 発行の記録は、完了したものも含めて探す。
     *
     * @param event 待つイベントの条件（テストで作った ID などで、このテストのイベントだけに絞る）
     */
    public void awaitPublishedThenHandled(Predicate<Object> event) {
        await().atMost(TIMEOUT).until(() -> Stream.concat(
                        registry.findIncompletePublications().stream(), completedPublications.findAll().stream())
                .anyMatch(publication -> event.test(publication.getEvent())));
        awaitHandled(event);
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
