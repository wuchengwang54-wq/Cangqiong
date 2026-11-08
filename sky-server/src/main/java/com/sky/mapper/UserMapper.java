package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    /*
        根据openid查询用户
         */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    /*
        根据id查询用户
         */
    @Select("select * from user where id = #{id}")
    User getById(Long id);

    //插入数据
    void insert(User user);
}
