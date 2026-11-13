package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SetmealMapper extends BaseMapper<Setmeal> {

    /**
     * 套餐分页查询（关联分类表带出 categoryName）
     */
    @Select({"<script>",
            "select s.*, c.name categoryName",
            "from setmeal s left join category c on s.category_id = c.id",
            "<where>",
            "  <if test=\"dto.name != null and dto.name != ''\">",
            "    and s.name like concat('%', #{dto.name}, '%')",
            "  </if>",
            "  <if test='dto.status != null'>",
            "    and s.status = #{dto.status}",
            "  </if>",
            "  <if test='dto.categoryId != null'>",
            "    and s.category_id = #{dto.categoryId}",
            "  </if>",
            "</where>",
            "order by s.create_time desc",
            "</script>"})
    IPage<SetmealVO> pageQuery(IPage<SetmealVO> page, @Param("dto") SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 根据套餐id查询菜品选项
     */
    @Select("select sd.name, sd.copies, d.image, d.description " +
            "from setmeal_dish sd left join dish d on sd.dish_id = d.id " +
            "where sd.setmeal_id = #{setmealId}")
    List<DishItemVO> getDishItemBySetmealId(@Param("setmealId") Long setmealId);
}
