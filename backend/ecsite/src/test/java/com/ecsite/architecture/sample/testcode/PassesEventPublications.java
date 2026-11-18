package com.ecsite.architecture.sample.testcode;

import org.springframework.modulith.events.core.EventPublicationRegistry;

import java.util.List;
import java.util.function.Function;

/**
 * イベントの発行の記録の検査の例：記録を読まない操作（規則に合う）
 *
 * <p>記録を補助に渡すだけの例、名前が find でも count でも始まらない操作の例、記録の型でない型の find の例。
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public final class PassesEventPublications {

    /**
     * インスタンスを作らない。
     */
    private PassesEventPublications() {
    }

    /**
     * 記録を補助に渡す。
     *
     * @param registry 記録
     * @param helper   記録を読む補助
     * @return 補助の結果
     */
    static Object pass(EventPublicationRegistry registry, Function<EventPublicationRegistry, Object> helper) {
        return helper.apply(registry);
    }

    /**
     * 名前が find でも count でも始まらない操作を呼ぶ。
     *
     * @param registry 記録
     */
    static void markFailed(EventPublicationRegistry registry) {
        registry.markStalePublicationsFailed(null);
    }

    /**
     * 記録の型でない型の find を呼ぶ。
     *
     * @param other 記録の型でない型
     * @return 結果
     */
    static List<String> otherType(OtherRepository other) {
        return other.findAll();
    }

    /**
     * 記録の型ではない Repository
     */
    interface OtherRepository {

        /**
         * 全てを返す。
         *
         * @return 全て
         */
        List<String> findAll();
    }
}
