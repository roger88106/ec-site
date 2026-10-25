package com.ecsite.architecture.cycle.domain.aggregate.vo;

import com.ecsite.architecture.cycle.domain.aggregate.OrderLike;

/**
 * 循環の例：値のパッケージ（vo）のクラスが、集約のパッケージのクラスを使う。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class AmountLike {

    /**
     * 集約のパッケージのクラスを使う項目
     */
    OrderLike owner;
}
