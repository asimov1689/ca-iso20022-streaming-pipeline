package com.gagroup.ca.enricher.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

// Caffeine is auto-configured from spring.cache.caffeine.spec in application.yml.
// No bean definitions needed here — Spring Boot wires it automatically.
// To switch to Redis in production (for multi-replica scaling):
//   Change spring.cache.type: redis in application.yml
//   Add spring.data.redis.host: redis-hostname
//   Zero Java code changes required — Spring Cache abstraction handles it.
@Configuration
@EnableCaching
public class CacheConfig {
}
