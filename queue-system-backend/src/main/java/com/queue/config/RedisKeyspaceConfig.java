package com.queue.config;

import com.queue.listener.TicketExpirationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import jakarta.annotation.PostConstruct;

/**
 * Redis Keyspace 通知配置：开启过期事件监听
 */
@Configuration
public class RedisKeyspaceConfig {

    private final StringRedisTemplate stringRedisTemplate;
    private final TicketExpirationListener ticketExpirationListener;

    public RedisKeyspaceConfig(StringRedisTemplate stringRedisTemplate,
                               TicketExpirationListener ticketExpirationListener) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.ticketExpirationListener = ticketExpirationListener;
    }

    @PostConstruct
    public void enableKeyspaceNotifications() {
        try {
            // 开启键空间通知：监听过期事件（E）和通用事件（x）
            stringRedisTemplate.getConnectionFactory().getConnection()
                .serverCommands().setConfig("notify-keyspace-events", "Ex");
        } catch (Exception e) {
            // 如果权限不足导致失败，记录警告但不阻塞启动
            System.err.println("WARN: 无法设置 Redis notify-keyspace-events 配置，可能需要手动在 redis.conf 中配置: " + e.getMessage());
        }
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // 监听 db0 的过期事件 __keyevent@0__:expired
        container.addMessageListener(ticketExpirationListener, new PatternTopic("__keyevent@*__:expired"));
        return container;
    }
}
