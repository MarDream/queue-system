package com.queue.util;

public class GenPassword {
    public static void main(String[] args) {
        String pwd = args.length > 0 ? args[0] : "admin123";
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(pwd);
        System.out.println(hash);
    }
}
