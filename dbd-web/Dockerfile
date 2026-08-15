# ============================================================
# Day-BY-Day 前端镜像（多阶段构建）
#   阶段 1：Node 构建 Vite 产物
#   阶段 2：nginx 托管静态资源 + /api 反代后端
# 构建：docker build -t dbd-web .
# ============================================================

# ---- 构建阶段 ----
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci || npm install
COPY . .
RUN npm run build

# ---- 运行阶段 ----
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
