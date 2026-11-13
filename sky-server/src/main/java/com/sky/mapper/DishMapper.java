package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    /**
     * 菜品分页查询（关联分类表带出 categoryName）
     * 分页由 MyBatis-Plus 分页插件完成，这里不写 limit
     */
    @Select({"<script>",
            "select d.id, d.name, d.category_id, d.price, d.image, d.description, d.status,",
            "       d.update_time, c.name as categoryName",
            "from dish d left join category c on d.category_id = c.id",
            "<where>",
            "  <if test=\"dto.name != null and dto.name != ''\">",
            "    and d.name like concat('%', #{dto.name}, '%')",
            "  </if>",
            "  <if test='dto.categoryId != null'>",
            "    and d.category_id = #{dto.categoryId}",
            "  </if>",
            "  <if test='dto.status != null'>",
            "    and d.status = #{dto.status}",
            "  </if>",
            "</where>",
            "order by d.update_time desc",
            "</script>"})
    IPage<DishVO> pageQuery(IPage<DishVO> page, @Param("dto") DishPageQueryDTO dishPageQueryDTO);

    /**
     * 根据套餐id查询菜品
     */
    @Select("select a.* from dish a left join setmeal_dish b on a.id = b.dish_id where b.setmeal_id = #{setmealId}")
    List<Dish> getBySetmealId(@Param("setmealId") Long setmealId);
}
