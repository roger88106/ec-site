package com.ecsite.module.payment.application.model.view;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 与信の反映を待つ支払
 *
 * @param id     支払 ID
 * @param pageId 支払画面 ID。支払画面がなければ null（壊れた行）
 */
public record AwaitingAuthorizationPaymentView(UUID id, @Nullable String pageId) {
}
