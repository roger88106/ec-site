package com.ecsite.architecture.sample.naming;

import org.springframework.stereotype.Service;

/**
 * 名前の形の検査の例：更新の service がクラスに @Transactional を付けていない（規則に合わない）
 *
 * <p>名前の形の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 * 抽象クラスにするのは、統合テストのコンポーネントスキャンで Bean として登録されないようにするため。
 */
@Service
public abstract class ExampleNonTransactionalCommandService {
}
