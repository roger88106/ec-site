package com.ecsite.module.catalog.infrastructure.persistence;

import com.ecsite.module.catalog.domain.product.PublicationStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jooq.Condition;

import java.time.Instant;

import static com.ecsite.jooq.catalog.Tables.PRODUCT;

/**
 * C 側に表示される条件の SQL。<br />
 * 一覧と検索の両方で、この部品だけを使う。検索だけに非公開の商品が出る事故を防ぐため（docs/design/modules/catalog.md）。
 * ドメインの {@code Product#isVisibleAt} と同じ条件で、両者の一致は統合テストで確認する。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProductVisibility {

    /**
     * 公開状態が「公開」で、公開期間内（開始を含み、終了を含まない。null は無限）であること。
     *
     * @param now 表示時刻
     * @return 条件
     */
    static Condition visibleAt(Instant now) {
        return PRODUCT.STATUS.eq(PublicationStatus.PUBLISHED.name())
                .and(PRODUCT.PUBLISH_START.isNull().or(PRODUCT.PUBLISH_START.le(now)))
                .and(PRODUCT.PUBLISH_END.isNull().or(PRODUCT.PUBLISH_END.gt(now)));
    }
}
