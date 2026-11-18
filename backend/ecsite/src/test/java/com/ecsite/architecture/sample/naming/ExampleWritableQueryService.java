package com.ecsite.architecture.sample.naming;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 名前の形の検査の例：参照の service がクラスの @Transactional に readOnly = true を付けていない（規則に合わない）
 *
 * <p>名前の形の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 * 抽象クラスにするのは、統合テストのコンポーネントスキャンで Bean として登録されないようにするため。
 */
@Transactional
@Service
public abstract class ExampleWritableQueryService {
}
