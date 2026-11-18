package com.ecsite.support;

import com.ecsite.config.event.ReadsEventPublicationRecords;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * テストのクラス
 *
 * <p>テストの書き方の検査が読む、単体テストと統合テストのクラス。
 * 置き場所は、Gradle が system property {@value #DIRS_PROPERTY} に渡す、両方のソースセットの出力のディレクトリ。
 *
 * <p>クラスファイルの読み込みは重いので、JVM ごとに 1 回だけ行う。
 * 違反する例のクラス（{@value #SAMPLE_PACKAGE} の下と、{@link #EXAMPLES_IN_PRODUCTION_PACKAGE}）は、検査の側で読み込むので含めない。
 */
public final class TestCodeClasses {

    /**
     * 単体テストと統合テストのクラスの置き場所を渡す system property の名前
     */
    public static final String DIRS_PROPERTY = "test-classes.dirs";

    /**
     * 違反する例のクラスのパッケージ
     */
    public static final String SAMPLE_PACKAGE = "com.ecsite.architecture.sample";

    /**
     * 本番のパッケージに置く、違反の例のクラス
     *
     * <p>package-private の型を呼ぶ例なので、{@value #SAMPLE_PACKAGE} の外の、その型と同じパッケージに置く。
     * 他のテストの検査が違反と数えないよう、一覧から外す。
     */
    public static final List<String> EXAMPLES_IN_PRODUCTION_PACKAGE = List.of(ReadsEventPublicationRecords.class.getName());

    /**
     * インスタンスを作らない。
     */
    private TestCodeClasses() {
    }

    /**
     * 単体テストと統合テストのクラスを返す。
     *
     * @return 単体テストと統合テストのクラス（違反する例を除く）
     * @throws IllegalStateException system property がないとき
     */
    public static JavaClasses all() {
        return Holder.ALL;
    }

    /**
     * system property から、クラスの置き場所を読む。
     *
     * @return 単体テストと統合テストの出力のディレクトリ
     * @throws IllegalStateException system property がないとき
     */
    private static List<Path> dirs() {
        String value = System.getProperty(DIRS_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("system property " + DIRS_PROPERTY + " がない（Gradle の test の task から流す）");
        }
        return Arrays.stream(value.split(File.pathSeparator)).map(Path::of).toList();
    }

    /**
     * テストのクラスを、遅延の static で JVM ごとに 1 回だけ読み込む入れ物。
     */
    private static final class Holder {

        /**
         * 単体テストと統合テストのクラス（違反する例を除く）
         */
        private static final JavaClasses ALL = new ClassFileImporter().importPaths(dirs()).that(DescribedPredicate.describe(
                "not samples", javaClass -> !ProductionClasses.isInPackage(javaClass.getPackageName(), SAMPLE_PACKAGE)
                        && !EXAMPLES_IN_PRODUCTION_PACKAGE.contains(ProductionClasses.outermostName(javaClass))));
    }
}
