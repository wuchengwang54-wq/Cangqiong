package com.sky.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sky.entity.Orders;
import com.sky.entity.User;
import com.sky.dto.GoodsSalesDTO;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import com.sky.exception.BaseException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;

    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        if (begin == null || end == null) {
            throw new BaseException("开始日期和结束日期不能为空");
        }
        if (begin.isAfter(end)) {
            throw new BaseException("开始日期不能晚于结束日期");
        }
        List<GoodsSalesDTO> sales = orderMapper.getSalesTop10(
                begin.atStartOfDay(), end.plusDays(1).atStartOfDay(), Orders.COMPLETED);
        List<String> names = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        for (GoodsSalesDTO item : sales) {
            names.add(item.getName());
            numbers.add(item.getNumber());
        }
        return SalesTop10ReportVO.builder()
                .nameList(StringUtils.join(names, ","))
                .numberList(StringUtils.join(numbers, ","))
                .build();
    }

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
            LocalDateTime beginTime = date.atStartOfDay();
            LocalDateTime endExclusive = date.plusDays(1).atStartOfDay();

            // 按下单时间归属日期，使用次日零点作为不包含的上界
            int count = toInt(orderMapper.selectCount(Wrappers.<Orders>lambdaQuery()
                    .ge(Orders::getOrderTime, beginTime)
                    .lt(Orders::getOrderTime, endExclusive)));
            int completed = toInt(orderMapper.selectCount(Wrappers.<Orders>lambdaQuery()
                    .ge(Orders::getOrderTime, beginTime)
                    .lt(Orders::getOrderTime, endExclusive)
                    .eq(Orders::getStatus, Orders.COMPLETED)));

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

    /**
     * 统计指定时间区间内的营业额数据
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
        while (!begin.equals(end)) {
            //日期计算，计算指定日期的后一天对应的日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //存放每天的营业额
        List<Double> turnoverList = new ArrayList<>();
        dateList.forEach(date -> {
            //查询date日期对应的营业额数据，营业额是指：状态为"已完成"的订单金额合计
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Double turnover = orderMapper.sumByWrapper(Wrappers.<Orders>lambdaQuery()
                    .ge(Orders::getOrderTime, beginTime)
                    .le(Orders::getOrderTime, endTime)
                    .eq(Orders::getStatus, Orders.COMPLETED));
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
        if (begin == null || end == null) {
            throw new BaseException("开始日期和结束日期不能为空");
        }
        if (begin.isAfter(end)) {
            throw new BaseException("开始日期不能晚于结束日期");
        }
        //存放从begin到end之间每天对应的日期
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //存放每天新增用户数量
        List<Integer> newUserList = new ArrayList<>();
        //存放每天的总用户数量
        List<Integer> totalUserList = new ArrayList<>();

        dateList.forEach(date -> {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            //统计总的用户数量
            int totalUser = toInt(userMapper.selectCount(Wrappers.<User>lambdaQuery()
                    .le(User::getCreateTime, endTime)));
            //统计新增的用户数量
            int newUser = toInt(userMapper.selectCount(Wrappers.<User>lambdaQuery()
                    .ge(User::getCreateTime, beginTime)
                    .le(User::getCreateTime, endTime)));

            totalUserList.add(totalUser);
            newUserList.add(newUser);
        });

        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();
    }

    //导出运营数据报表
    @Override
    public void exportBusinessData(HttpServletResponse response) throws IOException {
        //查询数据库，获取营业额数据--查询最近三十天营业数据
        LocalDate today = LocalDate.now();
        LocalDate dateBegin = today.minusDays(30);
        LocalDate dateEnd = today.minusDays(1);

        //查询概览数据
        BusinessDataVO businessDataVO = workspaceService.getBusinessData(LocalDateTime.of(dateBegin, LocalTime.MIN), LocalDateTime.of(dateEnd, LocalTime.MAX));

        //通过POI将数据写入到Excel文件中
        try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("Template/运营数据报表模板.xlsx")) {
            if (in == null) {
                throw new IOException("未找到运营数据报表模板");
            }

            //基于模版文件创建一个新的Excel文件
            try (XSSFWorkbook excel = new XSSFWorkbook(in)) {

                //获取表格文件的Sheet页
                XSSFSheet sheet = excel.getSheet("Sheet1");

                //填充数据--时间
                sheet.getRow(1).getCell(1).setCellValue("时间：" + dateBegin + "至" + dateEnd);
                //设置第四行数据
                XSSFRow row = sheet.getRow(3);
                row.getCell(2).setCellValue(businessDataVO.getTurnover());
                row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
                row.getCell(6).setCellValue(businessDataVO.getNewUsers());
                //设置第五行数据
                row = sheet.getRow(4);
                row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());
                row.getCell(4).setCellValue(businessDataVO.getUnitPrice());

                // 明细从第8行开始，按日期依次填充最近30个完整自然日的数据。
                int rowIndex = 7;
                for (LocalDate date = dateBegin; !date.isAfter(dateEnd); date = date.plusDays(1)) {
                    BusinessDataVO dailyData = workspaceService.getBusinessData(
                            date.atStartOfDay(), LocalDateTime.of(date, LocalTime.MAX));
                    row = sheet.getRow(rowIndex++);
                    row.getCell(1).setCellValue(date.toString());
                    row.getCell(2).setCellValue(dailyData.getTurnover());
                    row.getCell(3).setCellValue(dailyData.getValidOrderCount());
                    row.getCell(4).setCellValue(dailyData.getOrderCompletionRate());
                    row.getCell(5).setCellValue(dailyData.getUnitPrice());
                    row.getCell(6).setCellValue(dailyData.getNewUsers());
                }

                //通过数据流，将Excel文件下载到客户端浏览器
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setHeader("Content-Disposition", "attachment; filename=business-report-" + dateEnd + ".xlsx");
                ServletOutputStream out = response.getOutputStream();
                excel.write(out);
                out.flush();
            }
        }
    }

    private int toInt(Long value) {
        return value == null ? 0 : value.intValue();
    }
}
