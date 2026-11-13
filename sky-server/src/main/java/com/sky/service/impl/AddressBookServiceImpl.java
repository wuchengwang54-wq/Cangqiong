package com.sky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {
    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 条件查询
     */
    public List<AddressBook> list(AddressBook addressBook) {
        LambdaQueryWrapper<AddressBook> wrapper = Wrappers.<AddressBook>lambdaQuery()
                .eq(addressBook.getUserId() != null, AddressBook::getUserId, addressBook.getUserId())
                .eq(addressBook.getPhone() != null, AddressBook::getPhone, addressBook.getPhone())
                .eq(addressBook.getIsDefault() != null, AddressBook::getIsDefault, addressBook.getIsDefault());
        return addressBookMapper.selectList(wrapper);
    }

    /**
     * 新增地址
     */
    public void save(AddressBook addressBook) {
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);
        addressBookMapper.insert(addressBook);
    }

    /**
     * 根据id查询
     */
    public AddressBook getById(Long id) {
        return addressBookMapper.selectById(id);
    }

    /**
     * 根据id修改地址
     */
    public void update(AddressBook addressBook) {
        // 不允许通过修改接口变更地址所属用户
        addressBook.setUserId(null);
        addressBookMapper.update(addressBook, Wrappers.<AddressBook>lambdaUpdate()
                .eq(AddressBook::getId, addressBook.getId())
                .eq(AddressBook::getUserId, BaseContext.getCurrentId()));
    }

    /**
     * 设置默认地址
     */
    @Transactional
    public void setDefault(AddressBook addressBook) {
        //1、将当前用户的所有地址修改为非默认地址
        addressBookMapper.update(null, Wrappers.<AddressBook>lambdaUpdate()
                .set(AddressBook::getIsDefault, 0)
                .eq(AddressBook::getUserId, BaseContext.getCurrentId()));

        //2、将当前地址改为默认地址
        addressBookMapper.update(null, Wrappers.<AddressBook>lambdaUpdate()
                .set(AddressBook::getIsDefault, 1)
                .eq(AddressBook::getId, addressBook.getId())
                .eq(AddressBook::getUserId, BaseContext.getCurrentId()));
    }

    /**
     * 根据id删除地址
     */
    public void deleteById(Long id) {
        addressBookMapper.deleteById(id);
    }

}
