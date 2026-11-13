package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderDetailMapper extends BaseMapper<OrderDetail> {

    /**
     * 批量插入订单明细数据
     */
    @Insert({"<script>",
            "insert into order_detail (name, order_id, dish_id, setmeal_id, dish_flavor, number, amount, image) values",
            "<foreach collection='orderDetailList' item='od' separator=','>",
            "  (#{od.name}, #{od.orderId}, #{od.dishId}, #{od.setmealId},",
            "   #{od.dishFlavor}, #{od.number}, #{od.amount}, #{od.image})",
            "</foreach>",
            "</script>"})
    int insertBatch(@Param("orderDetailList") List<OrderDetail> orderDetailList);
}
