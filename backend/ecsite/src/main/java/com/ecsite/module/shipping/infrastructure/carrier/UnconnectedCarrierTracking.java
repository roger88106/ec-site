package com.ecsite.module.shipping.infrastructure.carrier;

import com.ecsite.module.shipping.application.port.CarrierTracking;
import com.ecsite.module.shipping.domain.Carrier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/**
 * 接続していない物流サービスの追跡（docs/03-機能仕様/shipping.md「物流サービス」）。<br />
 * 物流の mock フラグを立てないときに使う。実際の物流サービスを接続するまで、追跡の開始の依頼を受けても何も送らず、成功を返す。
 * 配送状態の通知が届かないので、配送状態は出荷済みから進まない。
 */
@Slf4j
@Component
@ConditionalOnBooleanProperty(name = "ecsite.shipping.mock.enabled", havingValue = false, matchIfMissing = true)
public class UnconnectedCarrierTracking implements CarrierTracking {

    /**
     * 物流サービスに接続していないことを、起動のときに 1 回、警告のログに出力する。<br />
     * フラグの設定漏れでも起動に成功するので、ログで気づけるようにする。
     */
    public UnconnectedCarrierTracking() {
        log.warn("物流サービスに接続していない。追跡を始めず、配送状態は進まない。mock を使うときは ecsite.shipping.mock.enabled=true を指定する");
    }

    @Override
    public void startTracking(Carrier carrier, String trackingNumber) {
        // 接続先がないので、何も送らない
    }
}
