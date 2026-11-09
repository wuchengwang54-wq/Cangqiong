package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {

    //插入数据
    public void insert(Orders orders);

    //根据订单号查询订单
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    //根据id查询订单
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    //修改订单信息
    void update(Orders orders);

    //根据状态和时间来查询订单
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(@Param("status") Integer status,
                                          @Param("orderTime") LocalDateTime orderTime);

    /**
     * 分页条件查询并按下单时间排序
     * @param ordersPageQueryDTO
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据状态统计订单数量
     * @param status
     */
    @Select("select count(id) from orders where status = #{status}")
    Integer countStatus(Integer status);
}
