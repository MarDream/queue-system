package com.queue.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

@Component
@Getter
public class ServerConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${app.frontend.port:5173}")
    private int frontendPort;

    @Value("${app.ip:}")
    private String configuredIp;

    @Value("${app.cors.extra-origins:}")
    private String extraCorsOrigins;

    @Getter
    private static String localIp = "127.0.0.1";

    @PostConstruct
    public void init() {
        if (configuredIp != null && !configuredIp.isBlank()) {
            localIp = configuredIp.trim();
        } else {
            localIp = getLocalIpAddress();
        }
        System.out.println("========================================");
        System.out.println("  Server IP  : " + localIp + (configuredIp != null && !configuredIp.isBlank() ? " (configured)" : " (auto-detect)"));
        System.out.println("  Backend    : http://" + localIp + ":" + serverPort);
        System.out.println("  Frontend   : http://" + localIp + ":" + frontendPort);
        System.out.println("========================================");
    }

    public String getBackendBaseUrl() {
        return "http://" + localIp + ":" + serverPort;
    }

    public String getFrontendBaseUrl() {
        return "http://" + localIp + ":" + frontendPort;
    }

    /**
     * 获取本机非回环 IP 地址
     */
    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isLoopbackAddress()) {
                        continue;
                    }
                    String ip = addr.getHostAddress();
                    // 优先返回 IPv4 地址，排除 IPv6 和 docker/虚拟网卡
                    if (ip != null && ip.indexOf('.') > 0 && !ip.startsWith("172.")) {
                        return ip;
                    }
                }
            }
            // 回退：取非回环地址
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
