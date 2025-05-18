package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.user.UserRegistrationRequest;
import com.cesco.scheduly.entity.UserEntity;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.exception.InvalidInputException;
import com.cesco.scheduly.exception.ResourceNotFoundException;
import com.cesco.scheduly.repository.UserCourseSelectionRepository;
import com.cesco.scheduly.repository.UserPreferenceRepository;
import com.cesco.scheduly.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
// import org.mockito.Mockito; // lenient() 사용 시 필요

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq; // eq() Matcher
// import static org.mockito.Mockito.lenient; // lenient() 사용 시
import static org.mockito.Mockito.*; // times, never, verify 등

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserCourseSelectionRepository userCourseSelectionRepository;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CourseDataService courseDataService; // 현재 userService에서 직접 사용 안 함 (validateCourseCodes가 없다면)

    @InjectMocks
    private UserService userService;

    private UserRegistrationRequest registrationRequest;
    private UserEntity sampleUser;
    private String sampleUserId = "test-uuid";

    @BeforeEach
    void setUp() {
        registrationRequest = new UserRegistrationRequest();
        registrationRequest.setUsername("testuser");
        registrationRequest.setPassword("password123");
        registrationRequest.setGrade("1");
        registrationRequest.setMajor("컴퓨터공학부");

        sampleUser = UserEntity.builder()
                .userId(sampleUserId)
                .username("testuser")
                .passwordHash("hashedPassword")
                .grade("1")
                .major("컴퓨터공학부")
                .build();
    }

    @Test
    @DisplayName("회원가입 성공 테스트")
    void registerUser_Success() {
        // Arrange
        when(userRepository.existsByUsername(registrationRequest.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(registrationRequest.getPassword())).thenReturn("hashedPassword");
        when(userRepository.save(any(UserEntity.class))).thenReturn(sampleUser);
        when(userCourseSelectionRepository.save(any(UserCourseSelectionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userPreferenceRepository.save(any(UserPreferenceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserEntity registeredUser = userService.registerUser(registrationRequest);

        // Assert
        assertNotNull(registeredUser);
        assertEquals(registrationRequest.getUsername(), registeredUser.getUsername());
        assertEquals("hashedPassword", registeredUser.getPasswordHash());
        verify(userRepository, times(1)).existsByUsername(anyString());
        verify(userRepository, times(1)).save(any(UserEntity.class));
        verify(userCourseSelectionRepository, times(1)).save(any(UserCourseSelectionEntity.class));
        verify(userPreferenceRepository, times(1)).save(any(UserPreferenceEntity.class));
    }

    @Test
    @DisplayName("회원가입 실패 테스트 - 사용자명 중복")
    void registerUser_UsernameAlreadyExists() {
        // Arrange
        when(userRepository.existsByUsername(registrationRequest.getUsername())).thenReturn(true);

        // Act & Assert
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            userService.registerUser(registrationRequest);
        });
        assertEquals("이미 사용 중인 사용자명입니다: " + registrationRequest.getUsername(), exception.getMessage());
        verify(userRepository, times(1)).existsByUsername(anyString());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("수강 이력 업데이트 테스트 - 성공")
    void updateTakenCourses_Success() {
        // Arrange
        List<String> courseCodes = List.of("CS101", "MA202");
        UserCourseSelectionEntity selectionEntity = UserCourseSelectionEntity.builder()
                .user(sampleUser)
                .takenCourses(new ArrayList<>())
                .mandatoryCourses(new ArrayList<>())
                .retakeCourses(new ArrayList<>())
                .build();

        // UserService.updateTakenCourses -> getUserCourseSelection -> userCourseSelectionRepository.findByUser_UserId 호출
        when(userCourseSelectionRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(selectionEntity));
        // userCourseSelectionRepository.save는 verify로 확인하거나, 필요시 반환값 스터빙
        // when(userCourseSelectionRepository.save(any(UserCourseSelectionEntity.class))).thenReturn(selectionEntity); // 이 스터빙은 현재 로직에서 사용되지 않을 수 있음

        // Act
        userService.updateTakenCourses(sampleUserId, courseCodes);

        // Assert
        assertEquals(courseCodes, selectionEntity.getTakenCourses());
        verify(userCourseSelectionRepository, times(1)).save(selectionEntity);
        // userRepository.findById는 getUserCourseSelection 내부 로직에 따라 호출될 수도, 안될 수도 있음.
        // 현재 UserService.getUserCourseSelection는 userRepository.findById를 직접 호출하지 않으므로 관련 스터빙 불필요.
    }

    @Test
    @DisplayName("수강 이력 업데이트 테스트 - 실패 (사용자 없음 또는 선택 정보 없음)")
    void updateTakenCourses_Fail_UserNotFound() {
        // Arrange
        String nonExistentUserId = "nonExistentUser";
        List<String> courseCodes = List.of("CS101");
        String expectedErrorMessage = "사용자 ID " + nonExistentUserId + "에 대한 과목 선택 정보를 찾을 수 없습니다.";

        // UserService.updateTakenCourses -> getUserCourseSelection -> userCourseSelectionRepository.findByUser_UserId 호출 시 Optional.empty() 반환
        when(userCourseSelectionRepository.findByUser_UserId(nonExistentUserId)).thenReturn(Optional.empty());
        // UserService의 getUserCourseSelection이 ResourceNotFoundException을 던지도록 되어 있음

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            userService.updateTakenCourses(nonExistentUserId, courseCodes);
        });
        assertEquals(expectedErrorMessage, exception.getMessage()); // 정확한 예외 메시지 확인

        verify(userCourseSelectionRepository, never()).save(any(UserCourseSelectionEntity.class));
        // userRepository.findById 호출 여부는 UserService.getUserCourseSelection 내부 로직에 따라 달라짐
        // (현재는 findByUser_UserId만으로 예외 발생 가능)
    }

    // TODO: getTakenCourses, updateMandatoryCourses, updateRetakeCourses,
    //       선호도 저장/조회(saveTimePreferences, getTimePreferences 등) 관련 메소드 테스트 추가
    //       각 테스트는 해당 메소드가 실제로 호출하는 repository 메소드에 대해서만 스터빙합니다.
}