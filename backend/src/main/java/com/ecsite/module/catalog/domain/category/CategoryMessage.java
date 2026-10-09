package com.ecsite.module.catalog.domain.category;

import com.ecsite.common.message.I18nMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * カテゴリのメッセージ
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CategoryMessage {

    /**
     * カテゴリの名称は 1 文字以上 50 文字以内にしてください。
     */
    public static final I18nMessage INVALID_CATEGORY_NAME = I18nMessage.of(Code.INVALID_CATEGORY_NAME);

    /**
     * カテゴリは 3 階層までしか作れません。
     */
    public static final I18nMessage CATEGORY_DEPTH_EXCEEDED = I18nMessage.of(Code.CATEGORY_DEPTH_EXCEEDED);

    /**
     * 同じ親の下に、同じ名称のカテゴリがすでにあります。
     */
    public static final I18nMessage CATEGORY_NAME_ALREADY_EXISTS = I18nMessage.of(Code.CATEGORY_NAME_ALREADY_EXISTS);

    /**
     * 並べ替えるカテゴリが、今の兄弟カテゴリと一致しません。再読み込みしてください。
     */
    public static final I18nMessage INVALID_CATEGORY_ORDER = I18nMessage.of(Code.INVALID_CATEGORY_ORDER);

    /**
     * 子カテゴリを持つカテゴリは削除できません。
     */
    public static final I18nMessage CATEGORY_HAS_CHILDREN = I18nMessage.of(Code.CATEGORY_HAS_CHILDREN);

    // ============================== コード定義 ==============================

    /**
     * メッセージコード
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Code {
        /**
         * カテゴリの名称は 1 文字以上 50 文字以内にしてください。
         */
        public static final String INVALID_CATEGORY_NAME = "ecsite.catalog.invalid_category_name";

        /**
         * カテゴリは 3 階層までしか作れません。
         */
        public static final String CATEGORY_DEPTH_EXCEEDED = "ecsite.catalog.category_depth_exceeded";

        /**
         * 同じ親の下に、同じ名称のカテゴリがすでにあります。
         */
        public static final String CATEGORY_NAME_ALREADY_EXISTS = "ecsite.catalog.category_name_already_exists";

        /**
         * 並べ替えるカテゴリが、今の兄弟カテゴリと一致しません。再読み込みしてください。
         */
        public static final String INVALID_CATEGORY_ORDER = "ecsite.catalog.invalid_category_order";

        /**
         * 子カテゴリを持つカテゴリは削除できません。
         */
        public static final String CATEGORY_HAS_CHILDREN = "ecsite.catalog.category_has_children";
    }
}
