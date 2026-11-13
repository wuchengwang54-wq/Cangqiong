package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DishFlavorMapper extends BaseMapper<DishFlavor> {

    /**
     * 批量插入口味数据
     */
    @Insert({"<script>",
            "insert into dish_flavor (dish_id, name, value) values",
            "<foreach collection='flavors' item='df' separator=','>",
            "  (#{df.dishId}, #{df.name}, #{df.value})",
            "</foreach>",
            "</script>"})
    int insertBatch(@Param("flavors") List<DishFlavor> flavors);
}
