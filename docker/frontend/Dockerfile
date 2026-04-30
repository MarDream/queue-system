ARG APP_VERSION=dev

FROM node:20-alpine AS builder
WORKDIR /build
ARG APP_VERSION=dev
ENV VITE_APP_VERSION=$APP_VERSION

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM alpine:3.20
LABEL maintainer="queue-system"
ARG APP_VERSION=dev
ENV APP_VERSION=$APP_VERSION

WORKDIR /dist
COPY --from=builder /build/dist ./

CMD ["sh", "-c", "mkdir -p /output && cp -r /dist/. /output/ && echo 'dist exported to /output'"]
