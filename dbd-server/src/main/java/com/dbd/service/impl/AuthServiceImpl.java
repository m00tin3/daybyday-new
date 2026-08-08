package com.dbd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dbd.common.BusinessException;
import com.dbd.dto.LoginDTO;
import com.dbd.dto.RegisterDTO;
import com.dbd.entity.User;
import com.dbd.mapper.UserMapper;
import com.dbd.service.AuthService;
import com.dbd.utils.RedisIdWorker;
import com.dbd.utils.UserContext;
import com.dbd.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 认证服务实现。
 * <p>Redis Key：</p>
 * <ul>
 *   <li>{@code dbd:verify:code:{phone}}    验证码（TTL 5 分钟）</li>
 *   <li>{@code dbd:verify:lock:{phone}}     防重发锁（TTL 60 秒，SETNX）</li>
 *   <li>{@code dbd:login:token:{token}}     token → userId（TTL 30 分钟，拦截器滑动续期）</li>
 * </ul>
 * <p>演示模式（app.sms.mock=true）：验证码固定 123456，不接真实短信通道；联调/面试演示可直接输入。</p>
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** 验证码 Key 前缀 */
    private static final String CODE_PREFIX = "dbd:verify:code:";

    /** 防重发锁 Key 前缀 */
    private static final String CODE_LOCK_PREFIX = "dbd:verify:lock:";

    /** 登录 token Key 前缀（值为 userId） */
    private static final String TOKEN_PREFIX = "dbd:login:token:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration CODE_LOCK_TTL = Duration.ofSeconds(60);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisIdWorker redisIdWorker;
    private final UserMapper userMapper;

    /** 演示模式开关：true 时验证码固定 123456 */
    @Value("${app.sms.mock:true}")
    private boolean smsMock;

    public AuthServiceImpl(StringRedisTemplate stringRedisTemplate,
                           RedisIdWorker redisIdWorker,
                           UserMapper userMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisIdWorker = redisIdWorker;
        this.userMapper = userMapper;
    }

    @Override
    public void sendCode(String phone) {
        if (!phone.matches("^1\\d{10}$")) {
            throw BusinessException.param("手机号格式不正确");
        }
        // SETNX 防 60 秒内重复发送
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(CODE_LOCK_PREFIX + phone, "1", CODE_LOCK_TTL);
        if (Boolean.FALSE.equals(locked)) {
            throw BusinessException.tooFast("发送太频繁，请 60 秒后再试");
        }
        // 演示模式固定验证码，生产模式生成 6 位随机数（接真实短信通道）
        String code = smsMock ? "123456" : String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        stringRedisTemplate.opsForValue().set(CODE_PREFIX + phone, code, CODE_TTL);
        // 演示模式打日志方便排查；生产模式在此调用短信服务商 API
        log.info("验证码已生成 phone={}, code={}（mock={}）", phone, code, smsMock);
    }

    @Override
    public Map<String, Object> login(LoginDTO dto) {
        verifyCode(dto.getPhone(), dto.getCode());

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (user == null) {
            user = createUser(dto.getPhone(), dto.getNickname());
        }
        return buildTokenResult(user);
    }

    @Override
    public Map<String, Object> register(RegisterDTO dto) {
        verifyCode(dto.getPhone(), dto.getCode());

        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (exists != null && exists > 0) {
            throw new BusinessException("该手机号已注册，请直接登录");
        }
        User user = createUser(dto.getPhone(), dto.getNickname());
        return buildTokenResult(user);
    }

    @Override
    public UserVO me() {
        Long userId = UserContext.get();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return UserVO.from(user);
    }

    /** 校验验证码：比对 Redis → 删除（一次性） */
    private void verifyCode(String phone, String code) {
        String key = CODE_PREFIX + phone;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached == null) {
            throw BusinessException.codeError("验证码已过期，请重新获取");
        }
        if (!cached.equals(code)) {
            throw BusinessException.codeError("验证码错误");
        }
        stringRedisTemplate.delete(key);
    }

    /** 首次登录自动注册（验证码模式无密码，password 存随机串） */
    private User createUser(String phone, String nickname) {
        User user = new User();
        user.setId(redisIdWorker.nextId("user"));
        user.setPhone(phone);
        user.setPassword(UUID.randomUUID().toString());
        user.setNickname(nickname == null || nickname.isBlank()
                ? "用户" + phone.substring(phone.length() - 4)
                : nickname);
        userMapper.insert(user);
        return user;
    }

    /** 生成 token 并写 Redis（30 分钟，拦截器每次请求滑动续期） */
    private Map<String, Object> buildTokenResult(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(TOKEN_PREFIX + token, String.valueOf(user.getId()), TOKEN_TTL);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userInfo", UserVO.from(user));
        return result;
    }
}
