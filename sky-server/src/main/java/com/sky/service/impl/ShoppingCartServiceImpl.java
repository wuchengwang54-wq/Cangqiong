package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sky.context.BaseContext;
import com.sky.constant.MessageConstant;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    @Override
    @Transactional
    public void subShoppingCart(ShoppingCartDTO dto) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new ShoppingCartBusinessException(MessageConstant.USER_NOT_LOGIN);
        }
        if (dto == null || (dto.getDishId() == null) == (dto.getSetmealId() == null)
                || (dto.getDishId() != null && dto.getDishId() <= 0)
                || (dto.getSetmealId() != null && dto.getSetmealId() <= 0)) {
            throw new ShoppingCartBusinessException("请提供一个有效的菜品ID或套餐ID");
        }

        LambdaQueryWrapper<ShoppingCart> wrapper = Wrappers.<ShoppingCart>lambdaQuery()
                .eq(ShoppingCart::getUserId, userId);

        if (dto.getDishId() != null) {
            wrapper.eq(ShoppingCart::getDishId, dto.getDishId())
                    .isNull(ShoppingCart::getSetmealId);
            if (dto.getDishFlavor() != null && !dto.getDishFlavor().isEmpty()) {
                wrapper.eq(ShoppingCart::getDishFlavor, dto.getDishFlavor());
            } else {
                wrapper.and(w -> w.isNull(ShoppingCart::getDishFlavor)
                        .or().eq(ShoppingCart::getDishFlavor, ""));
            }
        } else {
            wrapper.eq(ShoppingCart::getSetmealId, dto.getSetmealId())
                    .isNull(ShoppingCart::getDishId);
        }

        // 锁住匹配的条目，避免连续点击减号时丢失数量更新
        wrapper.orderByAsc(ShoppingCart::getId).last("limit 1 for update");
        ShoppingCart cart = shoppingCartMapper.selectOne(wrapper);
        if (cart == null) {
            return; // 已移除的条目无需重复操作
        }

        if (cart.getNumber() > 1) {
            cart.setNumber(cart.getNumber() - 1);
            shoppingCartMapper.updateById(cart);
        } else {
            shoppingCartMapper.delete(Wrappers.<ShoppingCart>lambdaQuery()
                    .eq(ShoppingCart::getId, cart.getId())
                    .eq(ShoppingCart::getUserId, userId));
        }
    }

    //添加购物车
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        //判断当前加入到购物车中的商品是否已经存在了
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);

        LambdaQueryWrapper<ShoppingCart> wrapper = Wrappers.<ShoppingCart>lambdaQuery()
                .eq(ShoppingCart::getUserId, userId)
                .eq(shoppingCart.getDishId() != null, ShoppingCart::getDishId, shoppingCart.getDishId())
                .eq(shoppingCart.getSetmealId() != null, ShoppingCart::getSetmealId, shoppingCart.getSetmealId())
                .eq(shoppingCart.getDishFlavor() != null, ShoppingCart::getDishFlavor, shoppingCart.getDishFlavor());

        List<ShoppingCart> list = shoppingCartMapper.selectList(wrapper);
        //如果已经存在，只需要将数量加一
        if (list != null && !list.isEmpty()) {
            ShoppingCart cart = list.get(0);
            cart.setNumber(cart.getNumber() + 1);
            shoppingCartMapper.updateById(cart);
        } else {
            //如果不存在，需要插入一条购物车数据

            //判断本次添加到购物车的是菜品还是套餐
            Long dishId = shoppingCartDTO.getDishId();
            if (dishId != null) {
                //本次添加购物车的是菜品
                Dish dish = dishMapper.selectById(dishId);
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
            } else {
                //本次添加购物车的是套餐
                Long setmealId = shoppingCartDTO.getSetmealId();

                Setmeal setmeal = setmealMapper.selectById(setmealId);
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }

            shoppingCart.setNumber(1);
            // create_time 由 MyBatis-Plus 自动填充
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    @Override
    public List<ShoppingCart> showShoppingCart() {
        //获取到当前微信用户的id
        Long userId = BaseContext.getCurrentId();
        return shoppingCartMapper.selectList(
                Wrappers.<ShoppingCart>lambdaQuery().eq(ShoppingCart::getUserId, userId));
    }

    @Override
    public void cleanShoppingCart() {
        //获取到当前微信用户的id
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.delete(Wrappers.<ShoppingCart>lambdaQuery()
                .eq(ShoppingCart::getUserId, userId));
    }

    @Override
    public int deleteShoppingCartItems(List<Long> ids) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new ShoppingCartBusinessException(MessageConstant.USER_NOT_LOGIN);
        }
        if (ids == null || ids.isEmpty()) {
            throw new ShoppingCartBusinessException("请选择要删除的购物车条目");
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ShoppingCartBusinessException("购物车条目ID必须为正整数");
        }
        // 用户ID来自登录上下文；不存在或不属于当前用户的条目不会被删除。
        return shoppingCartMapper.delete(Wrappers.<ShoppingCart>lambdaQuery()
                .in(ShoppingCart::getId, ids.stream().distinct().collect(Collectors.toList()))
                .eq(ShoppingCart::getUserId, userId));
    }
}
