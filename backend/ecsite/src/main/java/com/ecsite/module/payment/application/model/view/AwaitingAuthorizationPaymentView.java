package com.ecsite.module.payment.application.model.view;

import java.util.UUID;

/**
 * 与信の反映を待つ支払
 *
 * @param id     支払 ID
 * @param pageId 支払画面 ID
 */
public record AwaitingAuthorizationPaymentView(UUID id, String pageId) {
}
