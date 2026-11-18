package com.ecsite.architecture.sample.naming;

import org.springframework.stereotype.Service;

/**
 * 名前の形の検査の例：service の名前が QueryService・CommandService・GatewayService のどれでも終わらない（規則に合わない）
 *
 * <p>名前の形の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 * 抽象クラスにするのは、統合テストのコンポーネントスキャンで Bean として登録されないようにするため。
 */
@Service
public abstract class ExampleOrderService {
}
