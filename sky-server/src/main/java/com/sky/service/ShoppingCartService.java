package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {

    //添加购物车的接口方法
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);

    void subShoppingCart(ShoppingCartDTO shoppingCartDTO);

    List<ShoppingCart> showShoppingCart();

    //清空购物车
    void cleanShoppingCart();

    // 按购物车条目ID删除当前用户的商品，返回实际删除条目数
    int deleteShoppingCartItems(List<Long> ids);

}
