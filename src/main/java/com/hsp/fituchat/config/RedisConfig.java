package com.hsp.fituchat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@EnableCaching
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    /**
     * Redis 전용 RedisTemplate 빈 등록.
     * 키/값 모두 StringRedisSerializer를 사용하여 사람이 읽을 수 있는 문자열로 저장한다.
     * 기본 JdkSerializationRedisSerializer 대신 String 직렬화를 명시적으로 설정하여
     * Redis CLI에서 직접 데이터를 확인할 수 있고, 다른 언어/서버와 호환이 가능하다.
     * 채팅 메시지는 ObjectMapper로 JSON 직렬화한 뒤 이 템플릿으로 Pub/Sub 발행한다.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        RedisSerializationContext.SerializationPair<Object> jsonPair =
                RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer());
        RedisSerializationContext.SerializationPair<String> stringPair =
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer());

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(stringPair)
                .serializeValuesWith(jsonPair)
                .disableCachingNullValues();

        return RedisCacheManager.builder(cf)
                .withCacheConfiguration("user:name", base.entryTtl(Duration.ofHours(24)))
                .build();
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, String> t = new RedisTemplate<>();
        t.setConnectionFactory(cf);
        StringRedisSerializer s = new StringRedisSerializer();
        t.setKeySerializer(s);
        t.setValueSerializer(s);
        t.setHashKeySerializer(s);
        t.setHashValueSerializer(s);
        t.afterPropertiesSet();
        return t;
    }
}
