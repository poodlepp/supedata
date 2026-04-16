package com.dex.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DEX API 启动类
 */
@SpringBootApplication(scanBasePackages = "com.dex")
@MapperScan("com.dex.data.repository")
public class DexApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DexApiApplication.class, args);
    }
}
