package com.ecsite.common.id;

import java.util.UUID;

/**
 * 識別子の生成
 *
 * <p>識別子は、{@code UUID.randomUUID()} を直接呼ばずに、注入したこの interface で作る。テストでは、決まった値を返す実装に替えられる。
 */
public interface IdGenerator {

    /**
     * 新しい識別子を作る。
     *
     * @return UUID
     */
    UUID generate();
}
