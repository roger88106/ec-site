package com.ecsite.architecture.cycle.domain.aggregate;

import com.ecsite.architecture.cycle.domain.aggregate.vo.AmountLike;

/**
 * 循環の例：集約のパッケージのクラスが、値のパッケージ（vo）のクラスを使う。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class OrderLike {

    /**
     * 値のパッケージのクラスを使う項目
     */
    AmountLike amount;
}
