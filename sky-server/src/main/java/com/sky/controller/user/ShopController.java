package com.sky.controller.user;

import com.sky.result.Result;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")
@RequestMapping("/user/shop")
@ApiOperation("店铺相关接口")
@Slf4j
public class ShopController {

    public static final String KEY = "SHOP_STATUS";//设置常量

    @Autowired
    private RedisTemplate redisTemplate;

    /*
    获取店铺营业状态
     */
    @GetMapping("/status")
    @ApiOperation("获取店铺的营业状态")
    public Result<Integer> getStatus(){
        //value使用字符串序列化器存储，取出来是String，需要转换成Integer
        String value = (String) redisTemplate.opsForValue().get(KEY);
        //未设置过营业状态时默认按打烊处理，避免自动拆箱空指针
        Integer status = value == null ? 0 : Integer.valueOf(value);
        log.info("获取店铺的营业状态: {}", status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }
}
