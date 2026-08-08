package com.dbd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dbd.common.BusinessException;
import com.dbd.dto.LoginDTO;
import com.dbd.dto.RegisterDTO;
import com.dbd.entity.User;
import com.dbd.mapper.UserMapper;
import com.dbd.service.AuthService;
import com.dbd.utils.RedisIdWorker;
import com.dbd.utils.RedisKeyConstants;
import com.dbd.utils.UserContext;
import com.dbd.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 认证服务：验证码 → 登录（未注册自动注册）→ token 会话。
 * <p>演示模式（app.sms.mock=true，默认）：验证码固定 123456，不接真实短信通道。</p>
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

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
        // SETNX：60 秒内重复发送直接拒绝（原子，无并发问题）
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(RedisKeyConstants.VERIFY_CODE_LOCK + phone, "1", RedisKeyConstants.VERIFY_CODE_LOCK_TTL);
        if (Boolean.FALSE.equals(locked)) {
            throw BusinessException.tooFast("发送太频繁，请 60 秒后再试");
        }
        // 演示模式固定验证码，生产模式生成 6 位随机数并接入短信服务商
        String code = smsMock ? "123456" : String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        stringRedisTemplate.opsForValue().set(RedisKeyConstants.VERIFY_CODE + phone, code, RedisKeyConstants.VERIFY_CODE_TTL);
        log.info("验证码已生成 phone={}, code={}（mock={}）", phone, code, smsMock);
    }

    @Override
    public Map<String, Object> login(LoginDTO dto) {
        verifyCode(dto.getPhone(), dto.getCode());
        return buildTokenResult(findOrCreate(dto.getPhone(), dto.getNickname()));
    }

    @Override
    public Map<String, Object> register(RegisterDTO dto) {
        verifyCode(dto.getPhone(), dto.getCode());
        if (exists(dto.getPhone())) {
            throw new BusinessException("该手机号已注册，请直接登录");
        }
        return buildTokenResult(createUser(dto.getPhone(), dto.getNickname()));
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

    /** 校验验证码：与 Redis 比对后即删（一次性使用） */
    private void verifyCode(String phone, String code) {
        String key = RedisKeyConstants.VERIFY_CODE + phone;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached == null) {
            throw BusinessException.codeError("验证码已过期，请重新获取");
        }
        if (!cached.equals(code)) {
            throw BusinessException.codeError("验证码错误");
        }
        stringRedisTemplate.delete(key);
    }

    /** 登录专用：查用户，不存在则自动注册 */
    private User findOrCreate(String phone, String nickname) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        return user != null ? user : createUser(phone, nickname);
    }

    /** 该手机号是否已注册 */
    private boolean exists(String phone) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        return count != null && count > 0;
    }

    /** 注册用户：主键走全局 ID 生成器；验证码登录无密码，存随机串占位 */
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
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstants.LOGIN_TOKEN + token,
                String.valueOf(user.getId()),
                RedisKeyConstants.LOGIN_TOKEN_TTL);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userInfo", UserVO.from(user));
        return result;
    }
}
