# ============================================================
# Day-BY-Day 前端镜像（多阶段构建）
#   阶段 1：Node 构建 Vite 产物
#   阶段 2：nginx 托管静态资源 + /api 反代后端
# 构建：docker build -t dbd-web .
# ============================================================

# ---- 构建阶段 ----
FROM node:20-alpine AS build
WORKDIR /app

# 先复制锁文件，利用 Docker 层缓存
COPY package*.json ./

# 国内服务器：registry.npmjs.org 实测不可达（25s 超时），
# 改用 npmmirror（淘宝）镜像，实测 0.49s 响应
RUN npm config set registry https://registry.npmmirror.com && \
    (npm ci || npm install)

# 再复制源码构建（node_modules 已由 .dockerignore 排除，不会被覆盖）
COPY . .
RUN npm run build

# ---- 运行阶段 ----
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
