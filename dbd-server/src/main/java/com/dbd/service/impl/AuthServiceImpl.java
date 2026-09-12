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
import com.dbd.vo.UserSelfVO;
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
 * <p>管理员（role=1）在 app.admin.free-login=true 时可跳过验证码校验，
 * 详见 {@link #login}。</p>
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** 账号格式：手机号 11 位，或管理员标识；登录入参统一放宽为 6-20 位数字 */
    private static final String ACCOUNT_PATTERN = "^\\d{6,20}$";

    /**
     * 真实手机号格式：**注册（含登录时的自动注册）必须满足**。
     * <p>不能复用 {@link #ACCOUNT_PATTERN}——那个是为兼容管理员标识（如 2485617328）
     * 而放宽的，若用在注册路径会允许 "111111" 这类假号批量注册（线上已实际发生过）。</p>
     */
    private static final String PHONE_PATTERN = "^1\\d{10}$";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisIdWorker redisIdWorker;
    private final UserMapper userMapper;

    /** 演示模式开关：true 时验证码固定 123456 */
    @Value("${app.sms.mock:true}")
    private boolean smsMock;

    /**
     * 管理员免验证码登录开关。
     * <p>⚠️ 开启时，任何知道管理员账号标识的人都能直接进入管理后台，
     * 公开部署请通过 .env 的 {@code ADMIN_FREE_LOGIN=false} 关闭。</p>
     */
    @Value("${app.admin.free-login:true}")
    private boolean adminFreeLogin;

    public AuthServiceImpl(StringRedisTemplate stringRedisTemplate,
                           RedisIdWorker redisIdWorker,
                           UserMapper userMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisIdWorker = redisIdWorker;
        this.userMapper = userMapper;
    }

    @Override
    public void sendCode(String phone) {
        if (phone == null || !phone.matches(ACCOUNT_PATTERN)) {
            throw BusinessException.param("账号格式不正确（6-20 位数字）");
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
        User existing = findByPhone(dto.getPhone());

        // 管理员免验证码登录：仅对**已存在的 role=1 账号**生效（不存在的账号仍走正常校验，
        // 因此不会自动注册出管理员），且需显式开启开关。
        boolean freeLogin = existing != null && existing.isAdmin() && adminFreeLogin;
        if (freeLogin) {
            log.warn("管理员免验证码登录 account={}, userId={}（app.admin.free-login=true）",
                    dto.getPhone(), existing.getId());
        } else {
            verifyCode(dto.getPhone(), dto.getCode());
        }

        User user = existing != null ? existing : createUser(dto.getPhone(), dto.getNickname());
        return buildTokenResult(user);
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
    public UserSelfVO me() {
        Long userId = UserContext.get();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return UserSelfVO.from(user);
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

    /** 按登录账号查用户，不存在返回 null */
    private User findByPhone(String phone) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
    }

    /** 该账号是否已注册 */
    private boolean exists(String phone) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        return count != null && count > 0;
    }

    /**
     * 注册用户：主键走全局 ID 生成器；验证码登录无密码，存随机串占位。
     *
     * <p><b>这里必须再校验一次手机号格式。</b>{@link LoginDTO} 的账号校验为兼容
     * 管理员标识放宽成了 6-20 位数字，而登录接口对**不存在的账号会自动注册**——
     * 若不在此收口，任何人都能用 "111111" 这类假号注册出账号（线上已实际出现）。
     * 管理员标识不受影响：它已存在于库中，走 findByPhone 分支，不会进入本方法。</p>
     */
    private User createUser(String phone, String nickname) {
        if (!phone.matches(PHONE_PATTERN)) {
            throw BusinessException.param("账号格式不正确，请输入 11 位手机号");
        }
        User user = new User();
        user.setId(redisIdWorker.nextId("user"));
        user.setPhone(phone);
        user.setPassword(UUID.randomUUID().toString());
        user.setNickname(nickname == null || nickname.isBlank()
                ? "用户" + phone.substring(phone.length() - 4)
                : nickname);
        // 新注册一律为普通用户；管理员只能由种子数据/后台指定，避免越权自提
        user.setRole(User.ROLE_USER);
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
