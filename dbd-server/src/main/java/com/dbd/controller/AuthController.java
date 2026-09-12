package com.dbd.controller;

import com.dbd.common.Result;
import com.dbd.dto.LoginDTO;
import com.dbd.dto.RegisterDTO;
import com.dbd.service.AuthService;
import com.dbd.utils.UserContext;
import com.dbd.vo.UserSelfVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口（对应 API.md §3.1）。
 * <p>三个公开接口已在 WebConfig 白名单中，无需 token；/auth/me 需登录（🔒）。</p>
 */
@Tag(name = "认证模块")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "发送验证码")
    @PostMapping("/code")
    public Result<Void> sendCode(@RequestBody Map<String, String> body) {
        authService.sendCode(body.get("phone"));
        return Result.ok(null, "验证码已发送");
    }

    @Operation(summary = "验证码登录（未注册自动注册）")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    @Operation(summary = "注册（显式注册）")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    @Operation(summary = "当前登录用户完整资料 🔒（含手机号，仅本人可见）")
    @GetMapping("/me")
    public Result<UserSelfVO> me(HttpServletResponse response) {
        if (UserContext.get() == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return Result.fail(-1, "未登录");
        }
        return Result.ok(authService.me());
    }
}
