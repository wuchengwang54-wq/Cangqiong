package com.sky.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Orders> {

    /**
     * 统计指定时间区间内的销量排名前10
     * 按菜品 ID 合并不同口味，使用下单时的名称，不依赖菜品当前是否已删除
     */
    @Select("select max(od.name) as name, sum(od.number) as number " +
            "from order_detail od " +
            "inner join orders o on od.order_id = o.id " +
            "where o.status = #{status} " +
            "  and o.order_time >= #{begin} " +
            "  and o.order_time < #{endExclusive} " +
            "  and od.dish_id is not null " +
            "group by od.dish_id " +
            "order by number desc, od.dish_id asc " +
            "limit 10")
    List<GoodsSalesDTO> getSalesTop10(@Param("begin") LocalDateTime begin,
                                     @Param("endExclusive") LocalDateTime endExclusive,
                                     @Param("status") Integer status);

    /**
     * 动态条件统计订单金额，条件由 LambdaQueryWrapper 构造
     */
    @Select("select ifnull(sum(amount), 0) from orders ${ew.customSqlSegment}")
    Double sumByWrapper(@Param(Constants.WRAPPER) Wrapper<Orders> wrapper);
}
