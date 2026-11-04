package com.sky.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j
public class RedisConfiguration {

    @Bean
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        log.info("开始创建redis模版对象...");
        RedisTemplate redisTemplate = new RedisTemplate();
        //设置redis的连接工厂对象
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        //统一使用字符串序列化器，key和value在redis中都以明文形式存储
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        //设置redis key的序列化器
        redisTemplate.setKeySerializer(stringRedisSerializer);
        //设置redis value的序列化器
        redisTemplate.setValueSerializer(stringRedisSerializer);
        //设置hash类型key和value的序列化器
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        redisTemplate.setHashValueSerializer(stringRedisSerializer);
        return redisTemplate;
    }
}
