package com.company.recycle.web;

import com.company.recycle.db.ConnectionManager;
import com.company.recycle.db.DatabaseInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Spring Boot Web应用启动类
 */
@SpringBootApplication
public class WebApplication {
    private static final Logger logger = LoggerFactory.getLogger(WebApplication.class);
    
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("  启动财务回收对账系统 Web服务");
        logger.info("========================================");
        
        SpringApplication.run(WebApplication.class, args);
    }
    
    @PostConstruct
    public void init() {
        try {
            logger.info("初始化数据库连接...");
            ConnectionManager.initialize();
            DatabaseInitializer.initialize();
            logger.info("数据库初始化完成");
            logger.info("Web服务已启动，请访问: http://localhost:8080");
        } catch (Exception e) {
            logger.error("数据库初始化失败", e);
            throw new RuntimeException("数据库初始化失败", e);
        }
    }
    
    @PreDestroy
    public void cleanup() {
        logger.info("关闭数据库连接...");
        ConnectionManager.shutdown();
    }
    
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
