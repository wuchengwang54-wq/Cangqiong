package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sky.entity.SetmealDish;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public interface SetmealDishMapper extends BaseMapper<SetmealDish> {

    /**
     * 批量保存套餐和菜品的关联关系
     */
    @Insert({"<script>",
            "insert into setmeal_dish (setmeal_id, dish_id, name, price, copies) values",
            "<foreach collection='setmealDishes' item='sd' separator=','>",
            "  (#{sd.setmealId}, #{sd.dishId}, #{sd.name}, #{sd.price}, #{sd.copies})",
            "</foreach>",
            "</script>"})
    int insertBatch(@Param("setmealDishes") List<SetmealDish> setmealDishes);

    /**
     * 根据菜品id集合查询关联的套餐id
     */
    default List<Long> getSetmealIdByDishId(List<Long> dishIds) {
        return selectList(Wrappers.<SetmealDish>lambdaQuery()
                        .select(SetmealDish::getSetmealId)
                        .in(SetmealDish::getDishId, dishIds))
                .stream()
                .map(SetmealDish::getSetmealId)
                .distinct()
                .collect(Collectors.toList());
    }
}
