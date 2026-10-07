package com.example.attendance.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
@Profile("prod")
public class DatabaseConfig {

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() throws URISyntaxException {
        if (databaseUrl == null || databaseUrl.isEmpty() || !databaseUrl.contains("://")) {
            // Fallback for local testing if needed, though this is prod profile
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/attendance");
            config.setUsername("postgres");
            config.setPassword("postgres");
            config.setDriverClassName("org.postgresql.Driver");
            return new HikariDataSource(config);
        }
        
        // Render/Heroku provide DATABASE_URL in the format: postgres://user:password@host:port/dbname
        // Spring Boot expects: jdbc:postgresql://host:port/dbname
        URI dbUri = new URI(databaseUrl);
        
        String username = dbUri.getUserInfo() != null ? dbUri.getUserInfo().split(":")[0] : "";
        String password = dbUri.getUserInfo() != null && dbUri.getUserInfo().contains(":") ? dbUri.getUserInfo().split(":")[1] : "";
        
        String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + ':' + (dbUri.getPort() != -1 ? dbUri.getPort() : 5432) + dbUri.getPath();
        if (dbUri.getQuery() != null) {
            dbUrl += "?" + dbUri.getQuery();
        } else {
            // Render often requires SSL for external connections
            dbUrl += "?sslmode=require";
        }

        HikariConfig basicConfig = new HikariConfig();
        basicConfig.setJdbcUrl(dbUrl);
        basicConfig.setUsername(username);
        basicConfig.setPassword(password);
        basicConfig.setDriverClassName("org.postgresql.Driver");

        return new HikariDataSource(basicConfig);
    }
}
