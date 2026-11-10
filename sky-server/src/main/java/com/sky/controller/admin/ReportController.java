package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import com.sky.vo.OrderReportVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/*
数据统计相关接口
 */
@RestController
@RequestMapping("/admin/report")
@Api(tags = "数据统计相关接口")
@Slf4j
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/ordersStatistics")
    @ApiOperation("订单统计")
    public Result<OrderReportVO> ordersStatistics(
            @RequestParam("begin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate begin,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.success(reportService.getOrdersStatistics(begin, end));
    }

    @GetMapping("/turnoverStatistics")
    @ApiOperation("营业额统计")
    public Result<TurnoverReportVO> turnoverStatistics(
            @RequestParam("begin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate begin,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate end) {
        log.info("营业额数据分析：{}, {}", begin, end);
        return Result.success(reportService.getTurnoverStatistics(begin, end));
    }

    @GetMapping("/userStatistics")
    @ApiOperation("用户统计")
    public Result<UserReportVO> userStatistics(
            @RequestParam("begin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate begin,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate end
    ){
        log.info("用户数据分析：{}, {}", begin, end);
        return Result.success(reportService.getUserStatistics(begin, end));
    }
}
