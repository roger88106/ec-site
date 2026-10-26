package com.ecsite.architecture.layerdeps.module.sample.infrastructure;

import com.ecsite.architecture.layerdeps.module.sample.application.SampleProperties;
import com.ecsite.architecture.layerdeps.module.sample.application.port.SamplePort;
import com.ecsite.architecture.layerdeps.module.sample.domain.SampleDomain;

/**
 * 層の依存の検査の例：infrastructure から application の port と設定の値の型と domain を使う（表にある組なので規則に合う）。<br />
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
public class InfrastructureToPort {

    /**
     * 実装する port
     */
    SamplePort port;

    /**
     * 設定の値の型
     */
    SampleProperties properties;

    /**
     * 使う domain のクラス
     */
    SampleDomain domain;
}
