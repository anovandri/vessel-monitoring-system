package com.kreasipositif.vms.persistence.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Multi-Database Configuration
 * 
 * Configures connections to:
 * 1. PostgreSQL (Primary) - Current state with PostGIS
 * 2. ClickHouse - Historical analytics
 */
@Configuration
public class DatabaseConfig {

    // PostgreSQL Configuration
    @Value("${spring.datasource.url}")
    private String postgresUrl;

    @Value("${spring.datasource.username}")
    private String postgresUsername;

    @Value("${spring.datasource.password}")
    private String postgresPassword;

    @Value("${spring.datasource.hikari.maximum-pool-size:20}")
    private int postgresPoolSize;

    // ClickHouse Configuration
    @Value("${clickhouse.url}")
    private String clickhouseUrl;

    @Value("${clickhouse.username}")
    private String clickhouseUsername;

    @Value("${clickhouse.password}")
    private String clickhousePassword;

    @Value("${clickhouse.pool-size:10}")
    private int clickhousePoolSize;

    /**
     * PostgreSQL DataSource (Primary)
     */
    @Primary
    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgresUrl);
        config.setUsername(postgresUsername);
        config.setPassword(postgresPassword);
        config.setMaximumPoolSize(postgresPoolSize);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("PostgreSQLPool");
        
        // Enable PostGIS
        config.addDataSourceProperty("stringtype", "unspecified");
        
        return new HikariDataSource(config);
    }

    /**
     * JdbcTemplate for PostgreSQL operations
     */
    @Primary
    @Bean(name = "postgresJdbcTemplate")
    public JdbcTemplate postgresJdbcTemplate(
            @Qualifier("postgresDataSource") DataSource postgresDataSource) {
        return new JdbcTemplate(postgresDataSource);
    }

    /**
     * ClickHouse DataSource for time-series analytics
     */
    @Bean(name = "clickhouseDataSource")
    public DataSource clickhouseDataSource() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", clickhouseUsername);
        properties.setProperty("password", clickhousePassword);
        properties.setProperty("socket_timeout", "300000");
        properties.setProperty("max_execution_time", "300");
        
        return new ClickHouseDataSource(clickhouseUrl, properties);
    }

    /**
     * JdbcTemplate for ClickHouse operations
     */
    @Bean(name = "clickhouseJdbcTemplate")
    public JdbcTemplate clickhouseJdbcTemplate(
            @Qualifier("clickhouseDataSource") DataSource clickhouseDataSource) {
        return new JdbcTemplate(clickhouseDataSource);
    }
}
