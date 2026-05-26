package com.zoufx.ai.agent.memory.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zoufx.ai.agent.memory.property.MemoryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 记忆持久化层数据源装配。
 *
 * 独立的 DataSource / JdbcTemplate / TransactionTemplate 三件套，
 * 给 AnchorMemory（SqliteAnchorMemoryStore）/ ColdMemory（SqliteColdMemoryStore）/ HotMemory（SqliteHotMemoryStore）共用一套底座。
 *
 * 不使用 Spring Boot 自动配置的 spring.datasource.*：
 * - 保留业务语义命名空间 ai.memory.store.db-path
 * - 为未来 v0.3 多 DataSource 共存（如 SQLite + Postgres）留口子
 */
@Slf4j
@Configuration
public class MemoryDataSourceConfig {

    @Bean("memoryDataSource")
    public DataSource memoryDataSource(MemoryProperties props) throws IOException {
        // 确保 SQLite 文件父目录存在
        Path path = Paths.get(props.getStore().getDbPath());
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        HikariConfig cfg = new HikariConfig();
        String dbPath = props.getStore().getDbPath();
        cfg.setJdbcUrl("jdbc:sqlite:" + dbPath);
        // SQLite 单写并发限制，保守值；WAL 模式下读可并发
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        cfg.setPoolName("memory-sqlite-pool");
        // 每个新连接初始化时启用 WAL
        cfg.setConnectionInitSql("PRAGMA journal_mode=WAL");

        log.info("memoryDataSource initialized: {} (HikariCP max=5)", dbPath);
        return new HikariDataSource(cfg);
    }

    @Bean("memoryJdbcTemplate")
    public JdbcTemplate memoryJdbcTemplate(@Qualifier("memoryDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean("memoryTxTemplate")
    public TransactionTemplate memoryTxTemplate(@Qualifier("memoryDataSource") DataSource ds) {
        return new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
}
