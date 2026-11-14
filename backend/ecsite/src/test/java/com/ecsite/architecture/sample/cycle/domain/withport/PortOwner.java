package com.ecsite.architecture.sample.cycle.domain.withport;

import com.ecsite.architecture.sample.cycle.domain.withport.port.OwnerPort;

/**
 * 集約と port の例：集約のクラスが、集約の port を使う
 *
 * <p>port は集約と同じ slice なので、集約と port が互いに依存しても循環にしないことを確認するための例であり、本番のコードではない。
 */
public class PortOwner {

    /**
     * 集約の port を使う項目
     */
    OwnerPort port;
}
