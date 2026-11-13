package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类 Mapper：通用 CRUD 全部由 MyBatis-Plus 提供
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
