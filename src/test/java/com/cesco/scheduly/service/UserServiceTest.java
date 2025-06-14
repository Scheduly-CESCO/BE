package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.timetable.CreditRangeDto;
import com.cesco.scheduly.dto.timetable.CreditSettingsRequest;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.enums.DoubleMajorType;
import com.cesco.scheduly.repository.UserPreferenceRepository;
import com.cesco.scheduly.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;
    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("성공: 학점 설정이 유효할 때 정상적으로 저장되는지 테스트")
    void should_save_preferences_when_credit_goals_are_valid() {
        // given: 유효한 학점 목표 데이터 준비
        Long userId = 1L;
        User testUser = User.builder().id(userId).doubleMajorType(DoubleMajorType.DOUBLE_MAJOR).build();
        CreditSettingsRequest validSettings = new CreditSettingsRequest();
        validSettings.setMinTotalCredits(15);
        validSettings.setMaxTotalCredits(18);
        validSettings.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(9, 12),
                "이중전공", new CreditRangeDto(6, 6)
        ));

        // Mock 객체 동작 정의: DB에서 사용자 정보를 찾을 수 있도록 설정
        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));
        given(userPreferenceRepository.findByUser(testUser)).willReturn(Optional.of(new UserPreferenceEntity()));

        // when: 실제 테스트 대상인 public 메소드 호출
        userService.saveCreditAndCombinationPreferences(userId, validSettings);

        // then: 예외가 발생하지 않고, save 메소드가 호출되었는지 검증
        verify(userPreferenceRepository).save(any(UserPreferenceEntity.class));
    }

    @Test
    @DisplayName("실패: '전공심화 + 부전공' 사용자가 '이중전공' 학점을 0 초과로 설정 시 예외 발생 테스트")
    void should_throw_exception_when_intensive_minor_user_sets_double_major_credits() {
        // given: '이중전공' 사용자와 잘못된 학점 목표 데이터 준비
        Long userId = 1L;
        User testUser = User.builder().id(userId).doubleMajorType(DoubleMajorType.INTENSIVE_MINOR).build();
        CreditSettingsRequest invalidSettings = new CreditSettingsRequest();
        invalidSettings.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(15, 18),
                "이중전공", new CreditRangeDto(1, 3), // ★ 오류 유발 지점
                "부전공", new CreditRangeDto(3, 3)
        ));

        // Mock 객체 동작 정의
        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));

        // when & then: public 메소드를 호출했을 때, 내부의 private 메소드가 예외를 발생시키는지 검증
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.saveCreditAndCombinationPreferences(userId, invalidSettings);
        });

        // 발생한 예외 메시지가 올바른지 확인
        String expectedMessage = "'전공심화 + 부전공' 사용자는 '이중전공'에 0학점을 초과하여 설정할 수 없습니다.";
        assertTrue(exception.getMessage().contains(expectedMessage));

        // 예외가 발생했으므로, save 메소드는 절대 호출되지 않았음을 검증
        verify(userPreferenceRepository, never()).save(any(UserPreferenceEntity.class));
    }

    @Test
    @DisplayName("실패: '이중전공' 사용자가 '부전공' 학점을 0 초과로 설정 시 예외 발생 테스트")
    void should_throw_exception_when_double_major_user_sets_minor_credits() {
        // given: '이중전공' 사용자와 잘못된 학점 목표 데이터 준비
        Long userId = 1L;
        User testUser = User.builder().id(userId).doubleMajorType(DoubleMajorType.DOUBLE_MAJOR).build();
        CreditSettingsRequest invalidSettings = new CreditSettingsRequest();
        invalidSettings.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(9, 12),
                "이중전공", new CreditRangeDto(6, 6),
                "부전공", new CreditRangeDto(1, 3) // ★ 오류 유발 지점
        ));

        // Mock 객체 동작 정의
        given(userRepository.findById(userId)).willReturn(Optional.of(testUser));

        // when & then: public 메소드를 호출했을 때, 내부의 private 메소드가 예외를 발생시키는지 검증
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.saveCreditAndCombinationPreferences(userId, invalidSettings);
        });

        // 발생한 예외 메시지가 올바른지 확인
        String expectedMessage = "'이중전공' 사용자는 '부전공'에 0학점을 초과하여 설정할 수 없습니다.";
        assertTrue(exception.getMessage().contains(expectedMessage));

        // 예외가 발생했으므로, save 메소드는 절대 호출되지 않았음을 검증
        verify(userPreferenceRepository, never()).save(any(UserPreferenceEntity.class));
    }
}