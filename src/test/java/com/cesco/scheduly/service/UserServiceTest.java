package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.user.SignupRequest;
import com.cesco.scheduly.entity.User;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
    private CourseDataService courseDataService; // 현재 userService에서 직접 사용 안 함

    @InjectMocks
    private Userservice userService;

    private SignupRequest signupRequest;
    private User sampleUser;
    private String sampleUserId = "user-uuid-123"; // 실제 UUID 형식으로 변경해도 무방
    // 실제 학수번호 형식으로 변경된 예시
    private String courseCode1 = "M01207101"; // 예시: 머신러닝
    private String courseCode2 = "Y11113E11"; // 예시: 신입생세미나

    @BeforeEach
    void setUp() {
        signupRequest = new SignupRequest();
        signupRequest.setUsername("testuser");
        signupRequest.setPassword("password123");
        signupRequest.setGrade("1");
        signupRequest.setMajor("컴퓨터공학부");

        sampleUser = User.builder()
                .Id(Long.valueOf(sampleUserId))
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
        when(userRepository.existsByUsername(signupRequest.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(signupRequest.getPassword())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(userCourseSelectionRepository.save(any(UserCourseSelectionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userPreferenceRepository.save(any(UserPreferenceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        User registeredUser = userService.registerUser(signupRequest);

        // Assert
        assertNotNull(registeredUser);
        assertEquals(signupRequest.getUsername(), registeredUser.getUsername());
        assertEquals("hashedPassword", registeredUser.getPasswordHash());
        verify(userRepository, times(1)).existsByUsername(anyString());
        verify(userRepository, times(1)).save(any(User.class));
        verify(userCourseSelectionRepository, times(1)).save(any(UserCourseSelectionEntity.class));
        verify(userPreferenceRepository, times(1)).save(any(UserPreferenceEntity.class));
    }

    @Test
    @DisplayName("회원가입 실패 테스트 - 사용자명 중복")
    void registerUser_UsernameAlreadyExists() {
        // Arrange
        when(userRepository.existsByUsername(signupRequest.getUsername())).thenReturn(true);

        // Act & Assert
        InvalidInputException exception = assertThrows(InvalidInputException.class, () -> {
            userService.registerUser(signupRequest);
        });
        assertEquals("이미 사용 중인 사용자명입니다: " + signupRequest.getUsername(), exception.getMessage());
        verify(userRepository, times(1)).existsByUsername(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("수강 이력 업데이트 테스트 - 성공")
    void updateTakenCourses_Success() {
        // Arrange
        List<String> courseCodesToUpdate = List.of(courseCode1, courseCode2);
        UserCourseSelectionEntity selectionEntity = UserCourseSelectionEntity.builder()
                .user(sampleUser)
                .takenCourses(new ArrayList<>())
                .mandatoryCourses(new ArrayList<>())
                .retakeCourses(new ArrayList<>())
                .build();

        when(userCourseSelectionRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(selectionEntity));

        // Act
        userService.updateTakenCourses(sampleUserId, courseCodesToUpdate);

        // Assert
        assertEquals(courseCodesToUpdate, selectionEntity.getTakenCourses());
        verify(userCourseSelectionRepository, times(1)).save(selectionEntity);
    }

    @Test
    @DisplayName("수강 이력 업데이트 테스트 - 실패 (사용자 없음 또는 선택 정보 없음)")
    void updateTakenCourses_Fail_UserNotFound() {
        // Arrange
        String nonExistentUserId = "nonExistentUser";
        List<String> courseCodesToUpdate = List.of(courseCode1);
        String expectedErrorMessage = "사용자 ID " + nonExistentUserId + "에 대한 과목 선택 정보를 찾을 수 없습니다.";

        when(userCourseSelectionRepository.findByUser_UserId(nonExistentUserId)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            userService.updateTakenCourses(nonExistentUserId, courseCodesToUpdate);
        });
        assertEquals(expectedErrorMessage, exception.getMessage());

        verify(userCourseSelectionRepository, never()).save(any(UserCourseSelectionEntity.class));
    }

    // getTakenCourses, updateMandatoryCourses, updateRetakeCourses 등 다른 메소드들도
    // courseCode1, courseCode2 와 같은 실제 형식의 학수번호를 사용하도록 수정합니다.
    // 예시: getTakenCourses
    @Test
    @DisplayName("수강 이력 조회 테스트")
    void getTakenCourses_Success() {
        // Arrange
        List<String> expectedTakenCourses = List.of(courseCode1, courseCode2);
        UserCourseSelectionEntity selectionEntity = UserCourseSelectionEntity.builder()
                .user(sampleUser)
                .takenCourses(new ArrayList<>(expectedTakenCourses))
                .build();
        when(userCourseSelectionRepository.findByUser_UserId(sampleUserId)).thenReturn(Optional.of(selectionEntity));

        // Act
        List<String> actualTakenCourses = userService.getTakenCourses(sampleUserId);

        // Assert
        assertEquals(expectedTakenCourses, actualTakenCourses);
    }
}