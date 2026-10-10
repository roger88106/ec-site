package com.ecsite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * EC サイトのアプリケーション。
 */
@SpringBootApplication
public class EcSiteApplication {

    /**
     * アプリを起動する。
     */
    public static void main(String[] args) {
        SpringApplication.run(EcSiteApplication.class, args);
    }

}
