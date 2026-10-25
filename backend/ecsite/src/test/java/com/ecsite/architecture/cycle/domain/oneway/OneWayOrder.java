package com.ecsite.architecture.cycle.domain.oneway;

import com.ecsite.architecture.cycle.domain.oneway.vo.PlainAmount;

/**
 * 循環のない例：集約のパッケージのクラスが、値のパッケージ（vo）のクラスを使うだけで、逆の依存がない。<br />
 * 構成の検査が違反にしないことを確認するための例であり、本番のコードではない。
 */
public class OneWayOrder {

    /**
     * 値のパッケージのクラスを使う項目
     */
    PlainAmount amount;
}
