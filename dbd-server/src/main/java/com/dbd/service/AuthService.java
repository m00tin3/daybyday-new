package com.dbd.service;

import com.dbd.dto.LoginDTO;
import com.dbd.dto.RegisterDTO;
import com.dbd.vo.UserVO;

import java.util.Map;

/**
 * 认证服务：验证码、登录、注册、当前用户（对应 API.md §3.1）。
 */
public interface AuthService {

    /** 发送验证码：校验 + 60s 防重发，写入 Redis TTL 5 分钟 */
    void sendCode(String phone);

    /** 验证码登录：未注册自动注册，返回 { token, userInfo } */
    Map<String, Object> login(LoginDTO dto);

    /** 显式注册：手机号已存在则失败 */
    Map<String, Object> register(RegisterDTO dto);

    /** 当前登录用户信息 */
    UserVO me();
}
