package com.cesco.scheduly;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import static org.assertj.core.api.Assertions.assertThat; // AssertJ 사용 예시

@SpringBootTest // Spring Boot 애플리케이션 컨텍스트를 로드하여 통합 테스트 실행
class SchedulyApplicationTests {

    @Test
    void contextLoads(ApplicationContext context) {
        // 애플리케이션 컨텍스트가 성공적으로 로드되었는지 확인
        assertThat(context).isNotNull();
        System.out.println("Spring Application Context 로드 성공!");
    }

    // 필요하다면 특정 빈(Bean)이 컨텍스트에 잘 로드되었는지 확인하는 테스트 추가 가능
    // @Autowired
    // private UserService userService;
    //
    // @Test
    // void userServiceBeanIsLoaded() {
    //     assertThat(userService).isNotNull();
    //     System.out.println("UserService 빈 로드 성공!");
    // }
}