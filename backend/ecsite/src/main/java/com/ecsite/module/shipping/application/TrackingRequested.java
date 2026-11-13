package com.ecsite.module.shipping.application;

import com.ecsite.module.shipping.domain.Carrier;

import java.util.UUID;

/**
 * 追跡の開始の依頼
 *
 * <p>出荷のコミットの後に追跡を始めるための、出荷モジュールの中だけで使うイベント。
 * 出荷と同じトランザクションで記録されるので、物流サービスが失敗しても、出荷を止めずに再発行でやり直せる。
 *
 * @param shipmentId     出荷 ID
 * @param carrier        配送業者
 * @param trackingNumber 送り状番号（ハイフンを除いた数字）
 */
record TrackingRequested(UUID shipmentId, Carrier carrier, String trackingNumber) {
}
