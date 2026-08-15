package com.dbd;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Day-BY-Day 论坛后端启动类。
 * <p>包结构约定（com.dbd 下）：</p>
 * <ul>
 *   <li>common    —— 统一响应、业务异常、全局异常处理</li>
 *   <li>config    —— 拦截器 / Swagger 等配置</li>
 *   <li>controller —— 接口层（RESTful，路径前缀 /api）</li>
 *   <li>service   —— 业务层（缓存逻辑、Lua 脚本调用等）</li>
 *   <li>mapper    —— MyBatis-Plus Mapper</li>
 *   <li>entity/dto/vo —— 数据模型</li>
 *   <li>interceptor —— 登录态拦截器</li>
 *   <li>utils     —— 工具类（UserContext 等）</li>
 * </ul>
 * <p>@EnableScheduling：启用定时任务（热吧榜 5 分钟重算等）。</p>
 */
@SpringBootApplication
@MapperScan("com.dbd.mapper")
@EnableScheduling
@EnableAsync
public class DbdServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbdServerApplication.class, args);
    }
}
