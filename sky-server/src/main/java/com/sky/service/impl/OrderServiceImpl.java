package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
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
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Value("${sky.payment.mock-enabled:false}")
    private boolean mockPaymentEnabled;

    private static final int MOCK_PAY_METHOD = 0;

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
    @Autowired
    private WebSocketServer webSocketServer;

    //用户下单
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        //处理各种业务异常(地址簿为空，购物车数据为空)
        AddressBook addressBook = addressBookMapper.selectById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            //抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //查询当前用户的购物车数据
        Long userId = BaseContext.getCurrentId();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.selectList(
                Wrappers.<ShoppingCart>lambdaQuery().eq(ShoppingCart::getUserId, userId));

        if (CollectionUtils.isEmpty(shoppingCartList)) {
            //抛出业务异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        //向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
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
        for (ShoppingCart shoppingCart1 : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();//订单明细
            BeanUtils.copyProperties(shoppingCart1, orderDetail);
            orderDetail.setOrderId(orders.getId());//设置当前订单明细关联的订单id
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);
        //清空当前用户的购物车数据
        shoppingCartMapper.delete(Wrappers.<ShoppingCart>lambdaQuery()
                .eq(ShoppingCart::getUserId, userId));
        //封装VO返回结果
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .orderAmount(orders.getAmount())
                .build();
    }

    /**
     * 订单支付：调用微信支付下单接口，生成小程序调起支付所需的参数
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        //当前登录用户
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }

        //校验订单是否存在，且属于当前用户
        Orders ordersDB = getByNumber(ordersPaymentDTO.getOrderNumber());
        if (ordersDB == null || !userId.equals(ordersDB.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            if (Integer.valueOf(MOCK_PAY_METHOD).equals(ordersDB.getPayMethod())) {
                return OrderPaymentVO.builder().mock(true).build();
            }
            throw new OrderBusinessException("该订单已支付");
        }
        if (!Orders.PENDING_PAYMENT.equals(ordersDB.getStatus())
                || !Orders.UN_PAID.equals(ordersDB.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        if (mockPaymentEnabled) {
            // 原子更新：仅允许当前用户的待付款订单完成一次模拟支付
            LambdaUpdateWrapper<Orders> updateWrapper = Wrappers.<Orders>lambdaUpdate()
                    .set(Orders::getStatus, Orders.TO_BE_CONFIRMED)
                    .set(Orders::getPayStatus, Orders.PAID)
                    .set(Orders::getPayMethod, MOCK_PAY_METHOD)
                    .set(Orders::getCheckoutTime, LocalDateTime.now())
                    .eq(Orders::getNumber, ordersDB.getNumber())
                    .eq(Orders::getUserId, userId)
                    .eq(Orders::getStatus, Orders.PENDING_PAYMENT)
                    .eq(Orders::getPayStatus, Orders.UN_PAID);
            int updated = orderMapper.update(null, updateWrapper);
            if (updated == 0) {
                Orders latest = getByNumber(ordersDB.getNumber());
                if (latest == null || !Orders.PAID.equals(latest.getPayStatus())
                        || !Integer.valueOf(MOCK_PAY_METHOD).equals(latest.getPayMethod())) {
                    throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
                }
            } else {
                log.info("模拟支付成功，订单号：{}，未发生真实扣款", ordersDB.getNumber());
                Map<String, Object> message = new HashMap<>();
                message.put("type", 1);
                message.put("orderId", ordersDB.getId());
                message.put("content", "订单号：" + ordersDB.getNumber());
                webSocketServer.sendToAllClient(JSON.toJSONString(message));
            }
            return OrderPaymentVO.builder().mock(true).build();
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
        Orders ordersDB = getByNumber(outTradeNo);
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
        orderMapper.updateById(orders);
        log.info("订单{}支付成功，订单状态已更新为待接单", outTradeNo);

        //通过websocket向客户端浏览器推送消息 type orderId content
        Map<String, Object> map = new HashMap<>();
        map.put("type", 1);//1表示来单提醒  2表示客户催单
        map.put("orderId", orders.getId());
        map.put("content", "订单号：" + outTradeNo);

        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 用户端订单分页查询
     */
    @Override
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        // 设置分页
        Page<Orders> page = new Page<>(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        // 分页条件查询
        orderMapper.selectPage(page, buildPageQueryWrapper(ordersPageQueryDTO));

        List<OrderVO> list = new ArrayList<>();

        // 查询出订单明细，并封装入OrderVO进行响应
        if (page.getTotal() > 0) {
            for (Orders orders : page.getRecords()) {
                Long orderId = orders.getId();// 订单id

                // 查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.selectList(
                        Wrappers.<OrderDetail>lambdaQuery().eq(OrderDetail::getOrderId, orderId));

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), list);
    }

    /**
     * 查询订单详情
     */
    @Override
    public OrderVO details(Long id) {
        // 根据id查询订单
        Orders orders = orderMapper.selectById(id);

        // 查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(
                Wrappers.<OrderDetail>lambdaQuery().eq(OrderDetail::getOrderId, orders.getId()));

        // 将该订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 用户取消订单
     * 待付款、待接单状态可以取消；已支付的需要先退款
     */
    @Override
    public void userCancelById(Long id) throws Exception {
        Orders ordersDB = orderMapper.selectById(id);
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

        orderMapper.updateById(orders);
        log.info("用户取消订单成功，订单id：{}", id);
    }

    /**
     * 再来一单
     */
    @Override
    public void repetition(Long id) {
        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(
                Wrappers.<OrderDetail>lambdaQuery().eq(OrderDetail::getOrderId, id));

        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 商家端条件搜索订单
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        Page<Orders> page = new Page<>(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        orderMapper.selectPage(page, buildPageQueryWrapper(ordersPageQueryDTO));

        // 部分订单状态，需要额外返回订单菜品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page.getRecords());

        return new PageResult(page.getTotal(), orderVOList);
    }

    private List<OrderVO> getOrderVOList(List<Orders> ordersList) {
        // 需要返回订单菜品信息，自定义OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        if (!CollectionUtils.isEmpty(ordersList)) {
            for (Orders orders : ordersList) {
                // 将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                String orderDishes = getOrderDishesStr(orders);

                // 将订单菜品信息封装到orderVO中，并添加到orderVOList
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    /**
     * 根据订单id获取菜品信息字符串
     */
    private String getOrderDishesStr(Orders orders) {
        // 查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(
                Wrappers.<OrderDetail>lambdaQuery().eq(OrderDetail::getOrderId, orders.getId()));

        // 将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        // 将该订单对应的所有菜品信息拼接在一起
        return String.join("", orderDishList);
    }

    /**
     * 各个状态的订单数量统计
     */
    @Override
    public OrderStatisticsVO statistics() {
        // 根据状态，分别查询出待接单、待派送、派送中的订单数量
        int toBeConfirmed = countStatus(Orders.TO_BE_CONFIRMED);
        int confirmed = countStatus(Orders.CONFIRMED);
        int deliveryInProgress = countStatus(Orders.DELIVERY_IN_PROGRESS);

        // 将查询出的数据封装到orderStatisticsVO中响应
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();

        orderMapper.updateById(orders);
    }

    /**
     * 商家拒单
     * 只有待接单状态可以拒单，已支付的需要先退款
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        Orders ordersDB = orderMapper.selectById(ordersRejectionDTO.getId());
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

        orderMapper.updateById(orders);
        log.info("商家拒单成功，订单id：{}", ordersRejectionDTO.getId());
    }

    /**
     * 商家取消订单
     * 已完成、已取消的订单不能取消；已支付的需要先退款
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        Orders ordersDB = orderMapper.selectById(ordersCancelDTO.getId());
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

        orderMapper.updateById(orders);
        log.info("商家取消订单成功，订单id：{}", ordersCancelDTO.getId());
    }

    /**
     * 派送订单
     */
    @Override
    public void delivery(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.selectById(id);

        // 校验订单是否存在，并且状态为3
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为派送中
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);

        orderMapper.updateById(orders);
    }

    /**
     * 完成订单
     */
    @Override
    public void complete(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.selectById(id);

        // 校验订单是否存在，并且状态为4
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        // 更新订单状态,状态转为完成
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());

        orderMapper.updateById(orders);
    }

    /**
     * 微信退款成功回调
     * 微信会重复通知，需要保证幂等
     */
    @Override
    @Transactional
    public void refundSuccess(String outTradeNo) {
        Orders ordersDB = getByNumber(outTradeNo);
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
        orderMapper.updateById(orders);
        log.info("订单{}退款成功，支付状态已更新为退款", outTradeNo);
    }

    /**
     * 调用微信支付退款接口
     * 退款金额和原订单金额都使用订单表中的真实金额
     */
    private void refund(Orders orders) {
        // 根据订单自身的支付方式判断，关闭模拟开关后仍能取消历史模拟订单。
        if (Integer.valueOf(MOCK_PAY_METHOD).equals(orders.getPayMethod())) {
            log.info("模拟退款成功，订单号：{}，未调用微信退款", orders.getNumber());
            return;
        }
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

    /**
     * 客户催单
     */
    public void reminder(Long id) {
        //根据id查询订单
        Orders ordersDB = orderMapper.selectById(id);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("type", 2);
        map.put("orderId", id);
        map.put("content", "订单号" + ordersDB.getNumber());

        //通过webSocket向客户端推送消息
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 根据订单号查询订单
     */
    private Orders getByNumber(String number) {
        return orderMapper.selectOne(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getNumber, number));
    }

    /**
     * 构建订单分页查询条件（用户端历史订单、商家端订单搜索共用）
     */
    private LambdaQueryWrapper<Orders> buildPageQueryWrapper(OrdersPageQueryDTO dto) {
        return Wrappers.<Orders>lambdaQuery()
                .like(StringUtils.hasText(dto.getNumber()), Orders::getNumber, dto.getNumber())
                .like(StringUtils.hasText(dto.getPhone()), Orders::getPhone, dto.getPhone())
                .eq(dto.getUserId() != null, Orders::getUserId, dto.getUserId())
                .eq(dto.getStatus() != null, Orders::getStatus, dto.getStatus())
                .ge(dto.getBeginTime() != null, Orders::getOrderTime, dto.getBeginTime())
                .le(dto.getEndTime() != null, Orders::getOrderTime, dto.getEndTime())
                .orderByDesc(Orders::getOrderTime);
    }

    /**
     * 根据状态统计订单数量
     */
    private int countStatus(Integer status) {
        Long count = orderMapper.selectCount(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getStatus, status));
        return count == null ? 0 : count.intValue();
    }
}
