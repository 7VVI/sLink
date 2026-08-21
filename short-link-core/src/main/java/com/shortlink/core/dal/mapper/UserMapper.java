package com.shortlink.core.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortlink.core.dal.entity.UserDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper。
 */
public interface UserMapper extends BaseMapper<UserDO> {

    @Select("SELECT id, username, password, nickname, role, status, create_time, update_time "
            + "FROM sys_user WHERE username = #{username} LIMIT 1")
    UserDO selectByUsername(@Param("username") String username);
}
