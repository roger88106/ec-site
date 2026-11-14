package com.ecsite;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ロガーに出たログを取り込む、単体テストと統合テストで共有する補助
 *
 * <p>ログを確認するテストは、この補助だけを使う。テストのクラスに {@code @RegisterExtension} で置き、
 * 確認するロガー（ログを出す本番のクラス）を渡す。毎回のテストの前に取り込みを始め、後で外す。
 * <pre>{@code
 * @RegisterExtension
 * final LogCapture logs = LogCapture.of(PaymentExpiryJob.class);
 * }</pre>
 *
 * <p>取り込むのは渡したロガーに出たログだけで、出力の書式（JSON など）によらず、レベルと文を読める。
 * 1 つの処理が複数のクラスのロガーに出すときは、全てのクラスを渡す（ログは出た順に 1 つの一覧に入る）。
 * 取り込む間はロガーのレベルを {@code TRACE} にし、debug のログも取り込む。外すときに元のレベルに戻す。
 * ログに出してはいけない値がどのロガーにも出ないことを確認するときは、{@link #all} で全てのロガーのログを取り込む
 * （このときはレベルを変えず、設定のレベルで出るものだけを取り込む）。
 * 別のスレッド（非同期の処理）が出したログも取り込むので、統合テストでは、確認する処理の前に {@link #clear} で、
 * 準備や前のテストの処理が出したログを捨てる。
 */
public final class LogCapture implements BeforeEachCallback, AfterEachCallback {

    /**
     * 取り込むロガー
     */
    private final List<Logger> loggers;

    /**
     * 取り込む間、ロガーのレベルを {@code TRACE} にするか
     */
    private final boolean traceLevel;

    /**
     * 取り込んだログを貯める appender
     */
    private final Recorder recorder = new Recorder();

    /**
     * 取り込みを始める前のロガーのレベル。{@link #loggers} と同じ順（親のレベルを使うロガーは null）
     */
    private final List<Level> previousLevels = new ArrayList<>();

    /**
     * 指定したロガーのログを取り込む補助を作る。
     *
     * @param loggers    ロガー
     * @param traceLevel 取り込む間、ロガーのレベルを {@code TRACE} にするか
     */
    private LogCapture(List<Logger> loggers, boolean traceLevel) {
        this.loggers = loggers;
        this.traceLevel = traceLevel;
    }

    /**
     * 全てのロガー（root のロガー）のログを取り込む補助を作る。<br />
     * レベルは変えないので、設定のレベルで出力に出るログだけを取り込む。
     *
     * @return 補助
     */
    public static LogCapture all() {
        return new LogCapture(List.of((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)), false);
    }

    /**
     * クラスのロガー（{@code @Slf4j} が作るもの）のログを取り込む補助を作る。
     *
     * @param loggingClasses ログを出すクラス
     * @return 補助
     */
    public static LogCapture of(Class<?>... loggingClasses) {
        return new LogCapture(Arrays.stream(loggingClasses)
                .map(loggingClass -> (Logger) LoggerFactory.getLogger(loggingClass))
                .toList(), true);
    }

    /**
     * 名前で指定したロガーのログを取り込む補助を作る。<br />
     * 本番のクラスのロガーでなく、テストで作ったロガーを本番の処理に渡すときに使う。
     *
     * @param loggerName ロガーの名前
     * @return 補助
     */
    public static LogCapture of(String loggerName) {
        return new LogCapture(List.of((Logger) LoggerFactory.getLogger(loggerName)), true);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        start();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        stop();
    }

    /**
     * 取り込みを始める。<br />
     * {@code @RegisterExtension} で置いたときは、毎回のテストの前に呼ばれる。
     */
    public void start() {
        recorder.entries.clear();
        recorder.setContext(loggers.getFirst().getLoggerContext());
        recorder.start();
        previousLevels.clear();
        for (var logger : loggers) {
            previousLevels.add(logger.getLevel());
            if (traceLevel) {
                logger.setLevel(Level.TRACE);
            }
            logger.addAppender(recorder);
        }
    }

    /**
     * 取り込みを終え、ロガーのレベルを元に戻す。<br />
     * {@code @RegisterExtension} で置いたときは、毎回のテストの後に呼ばれる。
     */
    public void stop() {
        for (var index = 0; index < loggers.size(); index++) {
            loggers.get(index).detachAppender(recorder);
            if (traceLevel) {
                loggers.get(index).setLevel(previousLevels.get(index));
            }
        }
        recorder.stop();
    }

    /**
     * 今までに取り込んだログを捨てる。<br />
     * 確認する処理の前に呼び、準備や前のテストの非同期の処理が出したログを、確認から外す。
     */
    public void clear() {
        recorder.entries.clear();
    }

    /**
     * 取り込んだログを、出た順に返す。
     *
     * @return ログの一覧（呼んだ時点の写し）
     */
    public List<LogEntry> entries() {
        return List.copyOf(recorder.entries);
    }

    /**
     * 指定したレベルのログの文を、出た順に返す。
     *
     * @param level レベル
     * @return 文の一覧
     */
    public List<String> messages(org.slf4j.event.Level level) {
        return recorder.entries.stream()
                .filter(entry -> entry.level() == level)
                .map(LogEntry::message)
                .toList();
    }

    /**
     * 取り込んだログの全体を 1 つの文字列で返す。<br />
     * 文と、例外（原因と stack trace を含む）と、MDC と key-value の項目を全て含むので、ログに出してはいけない値が出ていないことの確認に使う。
     *
     * @return 全てのログを改行でつないだ文字列
     */
    public String text() {
        return String.join("\n", recorder.entries.stream().map(LogEntry::text).toList());
    }

    /**
     * 取り込んだ 1 件のログ
     *
     * @param level     レベル
     * @param message   引数を当てはめた文
     * @param details   文の他に出力に入る項目（例外の出力、MDC、key-value の項目）。なければ空の文字列
     */
    public record LogEntry(org.slf4j.event.Level level, String message, String details) {

        /**
         * 文と、文の他の項目をつないだ文字列を返す。
         *
         * @return 文と、文の他の項目
         */
        public String text() {
            return details.isEmpty() ? message : message + "\n" + details;
        }
    }

    /**
     * 出たログを貯める appender。<br />
     * 非同期の処理のスレッドからも書かれ、テストのスレッドが読むので、スレッドに安全な一覧に貯める。
     */
    private static final class Recorder extends AppenderBase<ILoggingEvent> {

        /**
         * 取り込んだログ
         */
        private final List<LogEntry> entries = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            var details = new ArrayList<String>();
            if (event.getThrowableProxy() != null) {
                details.add(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            }
            if (!event.getMDCPropertyMap().isEmpty()) {
                details.add(event.getMDCPropertyMap().toString());
            }
            if (event.getKeyValuePairs() != null) {
                details.add(event.getKeyValuePairs().toString());
            }
            entries.add(new LogEntry(org.slf4j.event.Level.valueOf(event.getLevel().levelStr),
                    event.getFormattedMessage(), String.join("\n", details)));
        }
    }
}
