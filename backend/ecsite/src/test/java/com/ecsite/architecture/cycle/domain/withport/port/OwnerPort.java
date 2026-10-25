package com.ecsite.architecture.cycle.domain.withport.port;

import com.ecsite.architecture.cycle.domain.withport.PortOwner;

/**
 * 集約と port の例：集約の port が、集約の型を返す。<br />
 * port は集約と同じ slice なので、集約と port が互いに依存しても循環にしないことを確認するための例であり、本番のコードではない。
 */
public interface OwnerPort {

    /**
     * 集約を返す。
     *
     * @return 集約
     */
    PortOwner find();
}
