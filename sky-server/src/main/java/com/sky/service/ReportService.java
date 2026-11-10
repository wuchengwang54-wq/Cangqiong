package com.sky.service;

import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;

import java.time.LocalDate;

public interface ReportService {
    //统计销售数前十的菜品
    SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end);

    // 按下单日期统计全部订单和已完成订单
    OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end);

    //统计指定时间区间内的营业额数据
    TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end);

    //统计指定时间区间内的营业额数据
    UserReportVO getUserStatistics(LocalDate begin, LocalDate end);


}
