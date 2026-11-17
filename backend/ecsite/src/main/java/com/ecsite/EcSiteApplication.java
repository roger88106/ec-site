package com.ecsite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

/**
 * EC サイトのアプリケーション
 *
 * <p>部品の名前は、クラスの完全修飾名にする。
 * 別のモジュールに、同じ単純名の部品（腐敗防止層の実装 {@code StockApiDirectory} など）があるためである。
 */
@SpringBootApplication(nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class EcSiteApplication {

    /**
     * アプリを起動する。
     *
     * @param args 起動の引数
     */
    public static void main(String[] args) {
        SpringApplication.run(EcSiteApplication.class, args);
    }

}
