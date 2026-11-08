package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.entity.User;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;

    //用户下单
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        //处理各种业务异常(地址簿为空，购物车数据为空)
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null){
            //抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        ShoppingCart shoppingCart = new ShoppingCart();
        //查询当前用户的购物车数据
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if(shoppingCartList == null || shoppingCartList.size()==0){
            //抛出业务异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        //向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));//使用时间戳当做订单号
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());//获取收货人
        orders.setAddress(addressBook.getDetail());//获取详细地址
        orders.setUserId(userId);//获取用户id

        orderMapper.insert(orders);
        List<OrderDetail> orderDetailList = new ArrayList<>();
        //向订单明细表插入多条数据
        for(ShoppingCart shoppingCart1 : shoppingCartList){
            OrderDetail orderDetail = new OrderDetail();//订单明细
            BeanUtils.copyProperties(shoppingCart1,orderDetail);
            orderDetail.setOrderId(orders.getId());//设置当前订单明细关联的订单id
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);
        //清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);
        //封装VO返回结果
        OrderSubmitVO submitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .orderAmount(orders.getAmount())
                .build();
        return submitVO;
    }

    /**
     * 订单支付：调用微信支付下单接口，生成小程序调起支付所需的参数
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        //当前登录用户
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);
        if (user == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }

        //校验订单是否存在，且属于当前用户
        Orders ordersDB = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        if (ordersDB == null || !userId.equals(ordersDB.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            throw new OrderBusinessException("该订单已支付");
        }

        JSONObject jsonObject;
        try {
            //调用微信支付统一下单接口，生成预支付交易单
            jsonObject = weChatPayUtil.pay(
                    ordersDB.getNumber(),          //商户订单号
                    ordersDB.getAmount(),          //支付金额，单位：元
                    "苍穹外卖订单-" + ordersDB.getNumber(), //商品描述
                    user.getOpenid()               //微信用户的openid
            );
        } catch (Exception e) {
            log.error("调用微信支付下单接口失败，订单号：{}", ordersDB.getNumber(), e);
            throw new OrderBusinessException("微信支付下单失败：" + e.getMessage());
        }

        //微信返回错误信息（例如商户号配置错误、签名失败等）时，没有 prepay_id
        if (jsonObject.getString("prepay_id") == null) {
            String message = jsonObject.getString("message");
            throw new OrderBusinessException(message != null ? message : "微信支付下单失败");
        }

        //封装小程序调起支付所需的参数
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        //VO 中字段名为 packageStr，需要单独取出微信返回的 package 字段
        vo.setPackageStr(jsonObject.getString("package"));
        return vo;
    }

    /**
     * 支付成功回调：修改订单状态为待接单、支付状态为已支付，并记录结账时间
     */
    @Override
    @Transactional
    public void paySuccess(String outTradeNo) {
        //根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //微信支付回调会重复通知，已支付则直接返回，保证幂等
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            log.info("订单{}已支付，忽略重复的支付回调", outTradeNo);
            return;
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
        log.info("订单{}支付成功，订单状态已更新为待接单", outTradeNo);
    }

    /**
     * 用户取消订单
     * 待付款、待接单状态可以取消；已支付的需要先退款
     */
    @Override
    public void userCancelById(Long id) throws Exception {
        Orders ordersDB = orderMapper.getById(id);
        //订单不存在，或者不属于当前用户
        if (ordersDB == null || !BaseContext.getCurrentId().equals(ordersDB.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //只有待付款、待接单状态可以取消
        if (!Orders.PENDING_PAYMENT.equals(ordersDB.getStatus())
                && !Orders.TO_BE_CONFIRMED.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.CANCELLED)
                .cancelReason("用户取消")
                .cancelTime(LocalDateTime.now())
                .build();

        //已支付的订单需要退款
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            refund(ordersDB);
            orders.setPayStatus(Orders.REFUND);
        }

        orderMapper.update(orders);
        log.info("用户取消订单成功，订单id：{}", id);
    }

    /**
     * 商家拒单
     * 只有待接单状态可以拒单，已支付的需要先退款
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());
        if (ordersDB == null || !Orders.TO_BE_CONFIRMED.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .build();

        //已支付的订单需要退款
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            refund(ordersDB);
            orders.setPayStatus(Orders.REFUND);
        }

        orderMapper.update(orders);
        log.info("商家拒单成功，订单id：{}", ordersRejectionDTO.getId());
    }

    /**
     * 商家取消订单
     * 已完成、已取消的订单不能取消；已支付的需要先退款
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.CANCELLED.equals(ordersDB.getStatus())
                || Orders.COMPLETED.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.CANCELLED)
                .cancelReason(ordersCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .build();

        //已支付的订单需要退款
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            refund(ordersDB);
            orders.setPayStatus(Orders.REFUND);
        }

        orderMapper.update(orders);
        log.info("商家取消订单成功，订单id：{}", ordersCancelDTO.getId());
    }

    /**
     * 微信退款成功回调
     * 微信会重复通知，需要保证幂等
     */
    @Override
    @Transactional
    public void refundSuccess(String outTradeNo) {
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.REFUND.equals(ordersDB.getPayStatus())) {
            log.info("订单{}已退款，忽略重复的退款回调", outTradeNo);
            return;
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.CANCELLED)
                .payStatus(Orders.REFUND)
                .build();
        //如果取消订单时还没有记录取消时间，这里补上
        if (ordersDB.getCancelTime() == null) {
            orders.setCancelTime(LocalDateTime.now());
        }
        orderMapper.update(orders);
        log.info("订单{}退款成功，支付状态已更新为退款", outTradeNo);
    }

    /**
     * 调用微信支付退款接口
     * 退款金额和原订单金额都使用订单表中的真实金额
     */
    private void refund(Orders orders) {
        try {
            String result = weChatPayUtil.refund(
                    orders.getNumber(),   //商户订单号
                    orders.getNumber(),   //商户退款单号（直接复用订单号，保证重试幂等）
                    orders.getAmount(),   //退款金额
                    orders.getAmount());  //原订单金额
            log.info("申请退款结果：{}", result);

            JSONObject jsonObject = JSON.parseObject(result);
            if (jsonObject == null || jsonObject.getString("code") != null) {
                String message = jsonObject == null ? "微信退款接口无响应" : jsonObject.getString("message");
                throw new OrderBusinessException(MessageConstant.REFUND_FAILED + "：" + (message == null ? "未知错误" : message));
            }
        } catch (OrderBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信退款接口失败，订单号：{}", orders.getNumber(), e);
            throw new OrderBusinessException(MessageConstant.REFUND_FAILED + "：" + e.getMessage());
        }
    }
}
