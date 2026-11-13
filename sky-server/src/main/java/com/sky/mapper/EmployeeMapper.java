package com.sky.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sky.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工 Mapper：通用 CRUD 全部由 MyBatis-Plus 提供
 */
@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
