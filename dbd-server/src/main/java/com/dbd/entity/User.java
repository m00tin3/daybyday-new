package com.dbd.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 {@code user}。
 * <p>主键由 Redis 全局 ID 生成器产生（时间戳 + 自增），数据库无 AUTO_INCREMENT；
 * {@code user} 为 MySQL 保留字，表名需反引号转义。</p>
 */
@Data
@TableName("`user`")
public class User {

    /** 用户ID（全局ID生成器） */
    @TableId
    private Long id;

    /** 手机号（登录账号） */
    private String phone;

    /** 密码（BCrypt 加密；验证码登录注册时存随机串） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String icon;

    /** 个性签名 */
    private String signText;

    /** 注册时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
