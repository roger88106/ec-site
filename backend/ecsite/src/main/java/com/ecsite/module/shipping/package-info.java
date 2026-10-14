/**
 * 出荷モジュール。<br />
 * 注文の出荷と配送状態を管理し、外部の物流サービスとのやり取りを包む（docs/backend/modules/shipping.md）。
 * 注文から API で呼ばれる。注文には依存せず、イベントも公開しない（依存が循環しないようにするため）。
 */
@org.springframework.modulith.ApplicationModule(id = "shipping", allowedDependencies = {})
package com.ecsite.module.shipping;
