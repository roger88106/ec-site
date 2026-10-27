package com.ecsite;

import org.springframework.boot.test.system.CapturedOutput;

/**
 * ログの確認で、そのテストの中で出た出力だけを見る補助。<br />
 * {@link CapturedOutput} は、同じクラスの前のテストの出力も含む。テストの始めに {@link #mark} で位置を覚え、
 * 確認では {@link #since} でその後の出力だけを読む。
 */
public final class CapturedLogs {

    private CapturedLogs() {
    }

    /**
     * 今までの出力の長さを返す。テストの始めに呼ぶ。
     */
    public static int mark(CapturedOutput output) {
        return output.getAll().length();
    }

    /**
     * {@link #mark} の後に出た出力を返す。
     */
    public static String since(CapturedOutput output, int mark) {
        return output.getAll().substring(mark);
    }
}
