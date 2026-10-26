package com.ecsite.module.shipping.application;

import com.ecsite.module.shipping.domain.Carrier;

import java.util.UUID;

/**
 * 追跡の開始の依頼。出荷を作ったときに登録し、コミットの後に物流サービスで追跡を始める。<br />
 * 出荷モジュールの中のイベントで、他のモジュールには公開しない。
 *
 * @param shipmentId     出荷 ID
 * @param carrier        配送業者
 * @param trackingNumber 送り状番号（ハイフンを除いた数字）
 */
record TrackingRequested(UUID shipmentId, Carrier carrier, String trackingNumber) {
}
