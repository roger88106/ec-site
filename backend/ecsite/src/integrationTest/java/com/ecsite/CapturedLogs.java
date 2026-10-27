package com.ecsite;

import org.springframework.boot.test.system.CapturedOutput;

/**
 * ログの確認で、確認する処理の後に出た出力だけを見る補助。<br />
 * {@link CapturedOutput} は、テストごとの capture を終了時に外すので、前のテストの中で出た出力は残らない。
 * ただし、テストの外で出た出力（Spring の起動のログ、前のテストの非同期の処理が後から出したログ）と、
 * 同じテストの中で確認の前に出た出力（準備の処理が出したログ）は残りうる。
 * 確認する処理の前に {@link #mark} で位置を覚え、確認では {@link #since} でその後の出力だけを読む。
 */
public final class CapturedLogs {

    /**
     * 補助の関数だけを持つので、作らせない。
     */
    private CapturedLogs() {
    }

    /**
     * 今までの出力の長さを返す。確認する処理の前に呼ぶ。
     *
     * @param output 取り込んだ出力
     * @return 今までの出力の長さ（{@link #since} に渡す位置）
     */
    public static int mark(CapturedOutput output) {
        return output.getAll().length();
    }

    /**
     * {@link #mark} の後に出た出力を返す。
     *
     * @param output 取り込んだ出力
     * @param mark   {@link #mark} が返した位置
     * @return その位置より後に出た出力
     */
    public static String since(CapturedOutput output, int mark) {
        return output.getAll().substring(mark);
    }
}
