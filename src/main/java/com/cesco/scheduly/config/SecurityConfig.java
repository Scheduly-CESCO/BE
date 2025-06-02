package com.cesco.scheduly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider; // JWT 필터 사용 시 주입

    public SecurityConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // REST API에서는 보통 CSRF 비활성화
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 사용 안 함 (토큰 기반 인증)
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui/html",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()

                        // 회원가입, 로그인은 누구나
                        .requestMatchers("/auth/signup", "/auth/login").permitAll()

                        // 강의 검색은 누구나 (CourseController)
                        .requestMatchers(HttpMethod.GET, "/courses/search").permitAll()

                        // /preferences 경로는 인증된 사용자만 (PreferencesController)
                        .requestMatchers("/preferences/**").authenticated()

                        // /lectures 경로는 인증된 사용자만 (LectureFilterController) - 기능 활성화 시
                        .requestMatchers("/lectures/**").authenticated()

                        // /users/{userId}/timetable/** 경로는 인증된 사용자만 (TimetableController)
                        .requestMatchers("/users/{userId}/timetable/**").authenticated()
                        // TODO: AuthController 남은 API가 있다면 경로 규칙 추가

                        // .requestMatchers("/users/**").authenticated() // 만약 UserController에 다른 API가 있다면
                        .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요 (permitAll() 대신)
                );

        // JWT 인증 필터를 UsernamePasswordAuthenticationFilter 앞에 추가하는 로직 (추후 구현)
        // http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
    // JwtAuthenticationFilter 클래스는 별도 구현 필요
}