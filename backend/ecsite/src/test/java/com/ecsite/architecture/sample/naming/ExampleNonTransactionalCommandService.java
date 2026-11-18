package com.ecsite.architecture.sample.naming;

import org.springframework.stereotype.Service;

/**
 * 名前の形の検査の例：更新の service がクラスに @Transactional を付けていない（規則に合わない）
 *
 * <p>構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 */
@Service
public class ExampleNonTransactionalCommandService {
}
