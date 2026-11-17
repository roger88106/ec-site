package com.ecsite.common.model.base;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 集約の登録したイベントの送り出し
 *
 * <p>リポジトリが、集約を保存した後に呼ぶ。保存に失敗したときにイベントだけが出ないよう、保存の前には呼ばない。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DomainEvents {

    /**
     * 集約の登録したイベントを取り出し、登録された順に送り出す。
     *
     * @param publisher イベントを発行する部品
     * @param aggregate 保存した集約
     */
    public static void publish(ApplicationEventPublisher publisher, AggregateRoot aggregate) {
        aggregate.pullEvents().forEach(publisher::publishEvent);
    }
}
