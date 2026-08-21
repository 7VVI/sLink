package com.shortlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 短链系统启动入口。
 *
 * <p>包扫描范围为 com.shortlink（覆盖 core 模块的基础组件）。</p>
 */
@EnableScheduling
@SpringBootApplication
public class ShortLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortLinkApplication.class, args);
    }
}
