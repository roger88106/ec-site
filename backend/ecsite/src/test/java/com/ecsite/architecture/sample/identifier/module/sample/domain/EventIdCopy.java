package com.ecsite.architecture.sample.identifier.module.sample.domain;

import java.util.UUID;

/**
 * 識別子の型の検査の例：受け取ったイベントの ID の写しを持つ記録（イベントではない）
 *
 * <p>イベント ID（最初の項目）は UUID のままでよく、それ以外の UUID は規則に合わない。
 * 構成の検査が違反を見つけられることを確認するための例であり、本番のコードではない。
 *
 * @param eventId 元のイベント ID（規則に合う）
 * @param orderId 注文の ID（UUID なので規則に合わない）
 */
public record EventIdCopy(UUID eventId, UUID orderId) {
}
