package com.ecsite.architecture.sample.testcode;

import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.TargetEventPublication;

import java.time.Duration;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * イベントの発行の記録の検査の例：補助の外で、記録を読む（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
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
}
