package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;

//自定义定时任务类
@Component
@Slf4j
public class MyTask {

    private final OrderMapper orderMapper;

    public MyTask(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    //定时任务  每隔五秒触发一次
    @Scheduled(cron = "*/5 * * * * ?")
    public void excuteTask() {
        log.info("定时任务待时执行：{}", new Date());
    }



}
