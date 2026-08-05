package com.dbd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dbd.entity.User;

/**
 * 用户 Mapper：继承 MyBatis-Plus BaseMapper，自动获得 CRUD / 条件查询能力。
 * 复杂统计 SQL 后续按需追加 @Select / XML。
 */
public interface UserMapper extends BaseMapper<User> {
}
