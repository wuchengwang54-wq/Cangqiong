package com.sky.service;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;

public interface OrderService {
    //用户下单
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    //订单支付，生成微信支付预支付交易单
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    //支付成功，修改订单状态
    void paySuccess(String outTradeNo);

    //用户取消订单（已支付则退款）
    void userCancelById(Long id) throws Exception;

    //商家拒单（已支付则退款）
    void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception;

    //商家取消订单（已支付则退款）
    void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception;

    //退款成功回调，修改订单状态
    void refundSuccess(String outTradeNo);
}
