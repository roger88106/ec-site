package com.ecsite.common.model.base;

import java.util.ArrayList;
import java.util.List;

/**
 * 集約の基底クラス。<br />
 * 状態を変えるメソッドの中でドメインイベントを登録し、リポジトリが保存した後に取り出して発行する（バックエンドのアーキテクチャの「モジュール内のアーキテクチャレベル」）。
 * <p>
 * 集約には jMolecules のアノテーションも付ける。名前が同じなので、アノテーションは完全修飾名で書く。
 * </p>
 * <pre>{@code
 * @org.jmolecules.ddd.annotation.AggregateRoot
 * public class TaxCategory extends AggregateRoot { ... }
 * }</pre>
 */
public abstract class AggregateRoot {

    /**
     * 登録されたイベント（永続化しない）
     */
    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * イベントを登録する。
     *
     * @param event イベント
     */
    protected void registerEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }

    /**
     * 登録されたイベントを取り出す。取り出したイベントは集約から消える。
     *
     * @return 登録された順のイベント
     */
    public List<DomainEvent> pullEvents() {
        var events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }
}
