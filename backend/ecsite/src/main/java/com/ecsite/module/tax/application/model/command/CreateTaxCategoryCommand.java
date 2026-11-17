package com.ecsite.module.tax.application.model.command;

import lombok.NonNull;

/**
 * 税区分の登録コマンド
 *
 * @param name 名称
 */
public record CreateTaxCategoryCommand(@NonNull String name) {
}
