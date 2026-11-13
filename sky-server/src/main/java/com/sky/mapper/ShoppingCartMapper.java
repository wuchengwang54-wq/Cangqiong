package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ShoppingCartMapper extends BaseMapper<ShoppingCart> {

    /**
     * 批量插入购物车数据（再来一单使用）
     */
    @Insert({"<script>",
            "insert into shopping_cart (name, image, user_id, dish_id, setmeal_id, dish_flavor, number, amount, create_time) values",
            "<foreach collection='shoppingCartList' item='sc' separator=','>",
            "  (#{sc.name}, #{sc.image}, #{sc.userId}, #{sc.dishId}, #{sc.setmealId},",
            "   #{sc.dishFlavor}, #{sc.number}, #{sc.amount}, #{sc.createTime})",
            "</foreach>",
            "</script>"})
    int insertBatch(@Param("shoppingCartList") List<ShoppingCart> shoppingCartList);
}
