// com/cesco/scheduly/config/SecurityConfig.java
package com.cesco.scheduly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        // 가장 구체적인 경로를 먼저 명시하고, 그 다음 일반적인 경로를 명시하는 것이 좋습니다.
                        .requestMatchers("/api/users/register", "/api/users/login").permitAll() // 회원가입, 로그인은 누구나 접근 가능
                        .requestMatchers("/api/courses/search").permitAll() // 강의 검색은 누구나 접근 가능 <<-- 이 부분을 명확히!
                        .requestMatchers("/api/users/{userId}/**").authenticated() // 사용자별 정보는 인증 필요
                        .requestMatchers("/api/**").authenticated() // 그 외 /api/** 경로는 인증 필요 (위에서 안 걸린 나머지)
                        .anyRequest().permitAll() // 개발 중 임시로 나머지 모든 요청 허용 (프로덕션에서는 denyAll() 또는 적절한 규칙)
                );

        // 만약 OAuth2 로그인을 사용한다면, 이 부분을 활성화할 수 있습니다.
        // http.oauth2Login(withDefaults());

        return http.build();
    }
}