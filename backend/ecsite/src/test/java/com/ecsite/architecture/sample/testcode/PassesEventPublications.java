package com.ecsite.architecture.sample.testcode;

import org.springframework.modulith.events.core.EventPublicationRegistry;

import java.util.function.Function;

/**
 * イベントの発行の記録の検査の例：記録を読まずに、補助に渡すだけ（規則に合う）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
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
}
