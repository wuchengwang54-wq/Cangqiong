package com.sky.service;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

public interface OrderService {
    //用户下单
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    //订单支付，生成微信支付预支付交易单
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    //支付成功，修改订单状态
    void paySuccess(String outTradeNo);

    //用户端订单分页查询
    PageResult pageQuery4User(int page, int pageSize, Integer status);

    //查询订单详情
    OrderVO details(Long id);

    //用户取消订单（已支付则退款）
    void userCancelById(Long id) throws Exception;

    //再来一单
    void repetition(Long id);

    //商家端条件搜索订单
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    //各个状态的订单数量统计
    OrderStatisticsVO statistics();

    //接单
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    //商家拒单（已支付则退款）
    void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception;

    //商家取消订单（已支付则退款）
    void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception;

    //派送订单
    void delivery(Long id);

    //完成订单
    void complete(Long id);

    //退款成功回调，修改订单状态
    void refundSuccess(String outTradeNo);

    //客户催单
    void reminder(Long id);
}
