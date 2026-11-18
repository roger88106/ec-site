package com.ecsite.architecture.sample.testcode;

import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.TargetEventPublication;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * イベントの発行の記録の検査の例：補助の外で、記録を読む（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 * 読む操作の名前は、例ごとに変えてある（検査の結果で、どの例が見つかったかを区別するため）。
 */
public final class ReadsEventPublications {

    /**
     * インスタンスを作らない。
     */
    private ReadsEventPublications() {
    }

    /**
     * 記録を呼び出しで読む。
     *
     * @param registry 記録
     * @return 終わっていない発行
     */
    static Collection<TargetEventPublication> call(EventPublicationRegistry registry) {
        return registry.findIncompletePublicationsOlderThan(Duration.ZERO);
    }

    /**
     * 記録をメソッド参照で読む。
     *
     * @param registry 記録
     * @return 終わっていない発行を返す関数
     */
    static Supplier<Collection<TargetEventPublication>> reference(EventPublicationRegistry registry) {
        return registry::findIncompletePublications;
    }

    /**
     * 記録の Repository を呼び出しで読む。
     *
     * @param repository 記録の Repository
     * @return 失敗した発行
     */
    static List<TargetEventPublication> repositoryCall(EventPublicationRepository repository) {
        return repository.findFailedPublications(null);
    }

    /**
     * 完了した記録を、メソッド参照で読む。
     *
     * @param completed 完了した記録
     * @return 完了した発行を返す関数
     */
    static Supplier<Collection<? extends EventPublication>> completedReference(CompletedEventPublications completed) {
        return completed::findAll;
    }

    /**
     * 記録を、ラムダの中で読む。
     *
     * @param repository 記録の Repository
     * @return 件数を返す関数
     */
    static Supplier<Integer> inLambda(EventPublicationRepository repository) {
        return () -> repository.countByStatus(EventPublication.Status.PUBLISHED);
    }

    /**
     * 記録を、private の補助のメソッドを経由して読む。
     *
     * @param repository 記録の Repository
     * @return 発行
     */
    static List<TargetEventPublication> viaHelper(EventPublicationRepository repository) {
        return readByStatus(repository);
    }

    /**
     * 記録を、実装の型で受けて読む。
     *
     * @param implementation 記録の Repository の実装
     * @return 完了した発行
     */
    static List<TargetEventPublication> implementationCall(Implementation implementation) {
        return implementation.findCompletedPublications();
    }

    /**
     * 記録を読む private の補助のメソッド
     *
     * @param repository 記録の Repository
     * @return 発行
     */
    private static List<TargetEventPublication> readByStatus(EventPublicationRepository repository) {
        return repository.findByStatus(EventPublication.Status.PUBLISHED);
    }

    /**
     * 記録の Repository の実装の型
     *
     * <p>本番の実装は package-private なので、同じ関係の型を例にする。
     */
    abstract static class Implementation implements EventPublicationRepository {
    }

    /**
     * 入れ子のクラス
     *
     * <p>テストの {@code @Nested} のクラスに当たる。
     */
    public static final class Inner {

        /**
         * インスタンスを作らない。
         */
        private Inner() {
        }

        /**
         * 入れ子のクラスの中で、記録を読む。
         *
         * @param repository 記録の Repository
         * @return 発行
         */
        static List<TargetEventPublication> nestedCall(EventPublicationRepository repository) {
            return repository.findIncompletePublicationsPublishedBefore(Instant.EPOCH);
        }
    }
}
