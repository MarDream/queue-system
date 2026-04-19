FROM node:20-alpine AS builder
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM alpine:3.20
LABEL maintainer="queue-system"

WORKDIR /dist
COPY --from=builder /build/dist ./

CMD ["sh", "-c", "mkdir -p /output && cp -r /dist/. /output/ && echo 'dist exported to /output'"]
