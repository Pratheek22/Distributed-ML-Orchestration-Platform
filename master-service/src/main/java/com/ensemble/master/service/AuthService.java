package com.ensemble.master.service;

import com.ensemble.master.entity.UserEntity;
import com.ensemble.master.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    // token -> [userId, expiryTime]
    private final Map<String, Object[]> tokenStore = new ConcurrentHashMap<>();

    public UserEntity register(String username, String email, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }
        UserEntity user = UserEntity.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .build();
        return userRepository.save(user);
    }

    public String login(String username, String password) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        String token = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusHours(8);
        tokenStore.put(token, new Object[]{user.getId(), expiry});
        return token;
    }

    public Long validateToken(String token) {
        Object[] entry = tokenStore.get(token);
        if (entry == null) throw new IllegalArgumentException("Invalid or expired token");
        LocalDateTime expiry = (LocalDateTime) entry[1];
        if (LocalDateTime.now().isAfter(expiry)) {
            tokenStore.remove(token);
            throw new IllegalArgumentException("Token expired");
        }
        return (Long) entry[0];
    }
}
