# ---- Stage 1: Build ----
FROM node:20-alpine AS builder
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- Stage 2: Serve ----
FROM nginx:1.27-alpine
LABEL maintainer="queue-system"

# 清理默认配置
RUN rm /etc/nginx/conf.d/default.conf

# 复制构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
