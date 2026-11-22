package com.ecsite.common.model.value;

import com.ecsite.common.model.id.OperatorId;
import lombok.NonNull;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * 操作した運営者（運営者の realm のアカウント）
 *
 * <p>氏名は操作の時点の写しで、運営者の氏名が後で変わっても変わらない。
 *
 * @param id   運営者 ID（token の subject）
 * @param name 氏名（姓 名）
 */
@ValueObject
public record Operator(@NonNull OperatorId id, @NonNull String name) {
}
