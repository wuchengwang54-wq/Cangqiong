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
        //统一使用字符串序列化器，key在redis中以明文形式存储
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        //设置redis key的序列化器
        redisTemplate.setKeySerializer(stringRedisSerializer);
        //设置hash类型key的序列化器
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        //value不设置，使用默认的JdkSerializationRedisSerializer
        //（缓存的对象需要实现Serializable，List<DishVO>可以正常序列化）
        return redisTemplate;
    }
}
