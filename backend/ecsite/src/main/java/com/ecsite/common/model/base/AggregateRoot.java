package com.ecsite.common.model.base;

import com.ecsite.common.error.CommonErrorCode;
import com.ecsite.common.error.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

/**
 * 集約の基底クラス
 *
 * <p>状態を変えるメソッドの中で登録したドメインイベントを、取り出すまで持つ。
 * 更新の競合を見つけるためのバージョンを、集約ごとに持たせる。
 *
 * <p>集約には jMolecules のアノテーションも付ける。名前が同じなので、アノテーションは完全修飾名で書く。
 * <pre>{@code
 * @org.jmolecules.ddd.annotation.AggregateRoot
 * public class TaxCategory extends AggregateRoot { ... }
 * }</pre>
 */
public abstract class AggregateRoot {

    /**
     * 登録されたイベント（永続化しない）
     */
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * バージョンを返す。
     *
     * <p>リポジトリが保存するたびに上げる。
     *
     * @return バージョン
     */
    public abstract long version();

    /**
     * 読んだ時点のバージョンと今のバージョンを比べる。
     *
     * <p>違えば、他の人が先に更新している。
     *
     * @param expected 読んだ時点のバージョン（リクエストのバージョン）
     * @throws BusinessException バージョンが違う（{@code CONCURRENT_MODIFICATION}）
     */
    public void requireVersion(long expected) {
        if (version() != expected) {
            throw BusinessException.of(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    /**
     * イベントを登録する。
     *
     * @param event イベント
     */
    protected void registerEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }

    /**
     * 登録されたイベントを取り出す。
     *
     * <p>取り出したイベントは集約から消える。
     *
     * @return 登録された順のイベント
     */
    public List<DomainEvent> pullEvents() {
        var events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }
}
