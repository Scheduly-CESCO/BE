package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.timetable.CreditRangeDto;
import com.cesco.scheduly.dto.timetable.CreditSettingsRequest;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserPreferenceEntity;
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

@ExtendWith(MockitoExtension.class) // Mockito 프레임워크를 사용하여 단위 테스트를 진행
class UserServiceTest {

    @InjectMocks // @Mock으로 만든 가짜 객체들을 이 클래스(UserService)에 주입합니다.
    private UserService userService;

    // UserService가 의존하는 리포지토리를 가짜(Mock) 객체로 만듭니다.
    // 이를 통해 DB와 상관없이 순수 서비스 로직만 테스트할 수 있습니다.
    @Mock
    private UserPreferenceRepository userPreferenceRepository;
    @Mock
    private UserRepository userRepository; // getUserDetails -> getUserPreference 호출 시 필요

    @Test
    @DisplayName("성공 케이스: 학점 설정이 유효할 때, 정상적으로 저장되는지 테스트")
    void should_save_preferences_when_credit_goals_are_valid() {
        // given: 1. 유효한 학점 목표 데이터를 준비합니다.
        Long userId = 1L;
        CreditSettingsRequest validSettings = new CreditSettingsRequest();
        validSettings.setMinTotalCredits(15);
        validSettings.setMaxTotalCredits(18);
        validSettings.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(9, 12),
                "교양", new CreditRangeDto(6, 6)
        )); // 최소합(15) <= 전체최소(15), 최대합(18) >= 전체최소(15) -> 유효

        // Mock 객체의 동작을 미리 정의합니다.
        given(userRepository.findById(userId)).willReturn(Optional.of(new User()));
        given(userPreferenceRepository.findByUser(any(User.class)))
                .willReturn(Optional.of(new UserPreferenceEntity()));

        // when: 2. 실제 테스트 대상 메소드(학점 설정 저장)를 호출합니다.
        userService.saveCreditAndCombinationPreferences(userId, validSettings);

        // then: 3. 예외가 발생하지 않고, save 메소드가 1번 호출되었는지 검증합니다.
        verify(userPreferenceRepository).save(any(UserPreferenceEntity.class));
    }

    @Test
    @DisplayName("실패 케이스 1: 유형별 최소학점 합이 전체 최대학점보다 클 때 예외 발생 테스트")
    void should_throw_exception_when_sum_of_min_type_credits_exceeds_max_total() {
        // given: 1. 의도적으로 잘못된 학점 목표 데이터를 준비합니다.
        Long userId = 1L;
        CreditSettingsRequest invalidSettings = new CreditSettingsRequest();
        invalidSettings.setMaxTotalCredits(18); // 전체 최대 18학점
        invalidSettings.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(12, 15),
                "이중전공", new CreditRangeDto(9, 12)
        )); // 유형별 최소 합 = 12 + 9 = 21 -> 전체 최대(18)보다 큼

        // when & then: 2. 학점 설정 저장 메소드를 호출했을 때,
        //               예상한 예외(IllegalArgumentException)가 발생하는지 검증합니다.
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.saveCreditAndCombinationPreferences(userId, invalidSettings);
        });

        // 3. 발생한 예외의 메시지가 우리가 정의한 내용과 일치하는지 확인합니다.
        String expectedMessage = "유형별 최소 학점의 합(21)이 전체 최대 학점 목표(18)를 초과할 수 없습니다.";
        assertTrue(exception.getMessage().contains(expectedMessage));

        // 4. 예외가 발생했으므로, save 메소드는 절대 호출되지 않았음을 검증합니다.
        verify(userPreferenceRepository, never()).save(any(UserPreferenceEntity.class));
    }

    @Test
    @DisplayName("실패 케이스 2: 유형별 최대학점 합이 전체 최소학점보다 작을 때 예외 발생 테스트")
    void should_throw_exception_when_sum_of_max_type_credits_is_less_than_min_total() {
        // given: 1. 의도적으로 잘못된 학점 목표 데이터를 준비합니다.
        Long userId = 1L;
        CreditSettingsRequest invalidSettings = new CreditSettingsRequest();
        invalidSettings.setMinTotalCredits(15); // 전체 최소 15학점
        invalidSettings.setCreditGoalsPerType(Map.of(
                "전공", new CreditRangeDto(3, 6),
                "교양", new CreditRangeDto(3, 6)
        )); // 유형별 최대 합 = 6 + 6 = 12 -> 전체 최소(15)보다 작음

        // when & then: 2. 예외가 발생하는지, 메시지는 올바른지 검증합니다.
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.saveCreditAndCombinationPreferences(userId, invalidSettings);
        });

        String expectedMessage = "유형별 최대 학점의 합(12)이 전체 최소 학점 목표(15)보다 작을 수 없습니다.";
        assertTrue(exception.getMessage().contains(expectedMessage));

        // 3. 예외가 발생했으므로, save 메소드는 절대 호출되지 않았음을 검증합니다.
        verify(userPreferenceRepository, never()).save(any(UserPreferenceEntity.class));
    }
}