package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.exception.BaseException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import com.sky.vo.OrderReportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        if (begin == null || end == null) {
            throw new BaseException("开始日期和结束日期不能为空");
        }
        if (begin.isAfter(end)) {
            throw new BaseException("开始日期不能晚于结束日期");
        }
        List<LocalDate> dates = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<Integer> completedCounts = new ArrayList<>();
        int total = 0;
        int completedTotal = 0;
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            Map<String, Object> filters = new HashMap<>();
            // 按下单时间归属日期，使用次日零点作为不包含的上界。
            filters.put("begin", date.atStartOfDay());
            filters.put("endExclusive", date.plusDays(1).atStartOfDay());
            Integer count = orderMapper.countByMap(filters);
            filters.put("status", Orders.COMPLETED);
            Integer completed = orderMapper.countByMap(filters);
            count = count == null ? 0 : count;
            completed = completed == null ? 0 : completed;
            dates.add(date);
            counts.add(count);
            completedCounts.add(completed);
            total += count;
            completedTotal += completed;
        }
        return OrderReportVO.builder()
                .dateList(StringUtils.join(dates, ","))
                .orderCountList(StringUtils.join(counts, ","))
                .validOrderCountList(StringUtils.join(completedCounts, ","))
                .totalOrderCount(total)
                .validOrderCount(completedTotal)
                .orderCompletionRate(total == 0 ? 0.0 : (double) completedTotal / total)
                .build();
    }

    /*
    统计指定时间区间内的营业额数据
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        if (begin == null || end == null) {
            throw new BaseException("开始日期和结束日期不能为空");
        }
        if (begin.isAfter(end)) {
            throw new BaseException("开始日期不能晚于结束日期");
        }
        //当前集合用于存放从begin到end范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);
        while(!begin.equals(end)){
            //日期计算，计算指定日期的后一天对应的日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //存放每天的营业额
        List<Double> turnoverList = new ArrayList<>();
        dateList.forEach(date->{
            //查询date日期对应的营业额数据，营业额是指：状态为"已完成"的订单金额合计
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            //select sum(amount) from orders where order_time > beginTime and order_time < endTime and status = 5
            Map<String, Object> map = new HashMap<>();
            map.put("begin",beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            turnoverList.add(turnover == null ? 0.0 : turnover);
        });

        //封装返回结果
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }

    //统计指定时间区间内的用户数据
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //存放从begin到end之间每天对应的日期
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);
        while(!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //存放每天新增用户数量 select count(id) from user where create_time < ? and create_time > ?
        List<Integer> newUserList = new ArrayList<>();
        //存放每天的总用户数量select count(id) from user where create_time < ?
        List<Integer> totalUserList = new ArrayList<>();

        dateList.forEach(date->{
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap<>();
            map.put("end",endTime);
            //统计总的用户数量
            Integer totalUser = userMapper.countByMap(map);
            //统计新增的用户数量
            map.put("begin",beginTime);
            Integer newUser = userMapper.countByMap(map);

            totalUserList.add(totalUser == null ? 0 : totalUser);
            newUserList.add(newUser == null ? 0 : newUser);

        });

        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();
    }
}
