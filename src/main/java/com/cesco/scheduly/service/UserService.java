package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.course.PreferencesRequest;
import com.cesco.scheduly.dto.timetable.CreditSettingsRequest;
import com.cesco.scheduly.dto.timetable.TimePreferenceRequest;
import com.cesco.scheduly.dto.user.SignupRequest;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.exception.AuthenticationException;
import com.cesco.scheduly.exception.ResourceNotFoundException;
import com.cesco.scheduly.exception.UserAlreadyExistsException;
import com.cesco.scheduly.repository.UserCourseSelectionRepository;
import com.cesco.scheduly.repository.UserPreferenceRepository;
import com.cesco.scheduly.repository.UserRepository;
import com.cesco.scheduly.util.GraduationRequirementUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserCourseSelectionRepository userCourseSelectionRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository,
                       UserCourseSelectionRepository userCourseSelectionRepository,
                       UserPreferenceRepository userPreferenceRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userCourseSelectionRepository = userCourseSelectionRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User signup(SignupRequest dto) {
        logger.info("Attempting to sign up new user with student ID: {}", dto.getStudentId());
        if (userRepository.existsByStudentId(dto.getStudentId())) {
            logger.warn("Signup failed: Student ID {} already exists.", dto.getStudentId());
            throw new UserAlreadyExistsException();
        }

        // 검증: 융합인재 모듈 선택 조건
        if ("융합인재전공".equals(dto.getMajor())) {
            if (dto.getModule1() == null || dto.getModule2() == null || dto.getModule3() == null) {
                throw new IllegalArgumentException("융합인재전공 1전공자는 모듈 3개를 모두 선택해야 합니다.");
            }
        } else if ("융합인재전공".equals(dto.getDouble_major())) {
            if (dto.getModule1() == null || dto.getModule2() == null) {
                throw new IllegalArgumentException("융합인재전공 이중전공자는 모듈 2개를 선택해야 합니다.");
            }
        }

        // User 엔티티에 @Builder, @NoArgsConstructor, @AllArgsConstructor 어노테이션이 있다고 가정
        // User.java에 @Builder.Default로 createdAt = LocalDateTime.now()가 설정되어 있어야 함
        User user = User.builder()
                .studentId(dto.getStudentId())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .name(dto.getName())
                .major(dto.getMajor())
                .doubleMajor(dto.getDouble_major()) // DTO의 필드명 snake_case 유의
                .grade(dto.getGrade())
                .semester(dto.getSemester())
                .college(dto.getCollege())
                .module1(dto.getModule1())
                .module2(dto.getModule2())
                .module3(dto.getModule3())
                // .createdAt(LocalDateTime.now()) // User 엔티티에서 @Builder.Default 처리 권장
                .build();
        User savedUser = userRepository.save(user);
        logger.info("User signed up successfully: {} (ID: {})", savedUser.getStudentId(), savedUser.getId());

        // UserCourseSelectionEntity 자동 생성
        UserCourseSelectionEntity selection = UserCourseSelectionEntity.builder()
                .user(savedUser)
                // 엔티티의 @Builder.Default로 내부 리스트가 초기화된다고 가정
                .build();
        userCourseSelectionRepository.save(selection);
        logger.info("Initialized UserCourseSelectionEntity for user ID: {}", savedUser.getId());

        // UserPreferenceEntity 자동 생성
        UserPreferenceEntity preference = UserPreferenceEntity.builder()
                .user(savedUser)
                // 엔티티의 @Builder.Default로 내부 DTO 객체가 초기화된다고 가정
                .build();
        userPreferenceRepository.save(preference);
        logger.info("Initialized UserPreferenceEntity for user ID: {}", savedUser.getId());

        return savedUser;
    }

    @Transactional(readOnly = true)
    public User authenticate(String studentId, String password) {
        logger.info("Attempting to authenticate user with student ID: {}", studentId);
        User user = userRepository.findByStudentId(studentId)
                .orElseThrow(() -> {
                    logger.warn("Authentication failed: User not found with student ID: {}", studentId);
                    return new AuthenticationException(); // 생성자에 메시지 받는 것 고려
                });
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            logger.warn("Authentication failed: Invalid password for student ID: {}", studentId);
            throw new AuthenticationException();
        }
        logger.info("User authenticated successfully: {}", studentId);
        return user;
    }

    @Transactional(readOnly = true)
    public User getUserDetails(Long userId) { // User의 PK는 Long 타입
        logger.debug("Fetching user details for user ID: {}", userId);
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("User details not found for user ID: {}", userId);
                    return new ResourceNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId);
                });
    }

    @Transactional(readOnly = true)
    public UserCourseSelectionEntity getUserCourseSelection(Long userId) {
        User user = getUserDetails(userId); // 사용자 존재 확인 선행
        return userCourseSelectionRepository.findByUser(user) // User 객체로 조회
                .orElseGet(() -> {
                    logger.warn("UserCourseSelectionEntity not found for user ID: {}. Creating new one.", userId);
                    UserCourseSelectionEntity newSelection = UserCourseSelectionEntity.builder().user(user).build();
                    return userCourseSelectionRepository.save(newSelection);
                });
    }

    @Transactional(readOnly = true)
    public UserPreferenceEntity getUserPreference(Long userId) {
        User user = getUserDetails(userId);
        return userPreferenceRepository.findByUser(user) // User 객체로 조회
                .orElseGet(() -> {
                    logger.warn("UserPreferenceEntity not found for user ID: {}. Creating new one.", userId);
                    UserPreferenceEntity newPreference = UserPreferenceEntity.builder().user(user).build();
                    return userPreferenceRepository.save(newPreference);
                });
    }

    @Transactional
    public void saveUserCourseSelections(Long userId, PreferencesRequest dto) {
        logger.info("Saving course selections for user ID: {}", userId);
        UserCourseSelectionEntity selection = getUserCourseSelection(userId);

        selection.setTakenCourses(dto.getCompleted_lectures() != null ? new ArrayList<>(dto.getCompleted_lectures()) : new ArrayList<>());
        selection.setMandatoryCourses(dto.getRequired_lectures() != null ? new ArrayList<>(dto.getRequired_lectures()) : new ArrayList<>());
        selection.setRetakeCourses(dto.getRetake_lectures() != null ? new ArrayList<>(dto.getRetake_lectures()) : new ArrayList<>());

        userCourseSelectionRepository.save(selection);
        logger.info("Course selections saved for user ID: {}. Data: {}", userId, dto);
    }

    @Transactional
    public int calculateGraduationCredits(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String studentId = user.getStudentId();
        int admissionYear = Integer.parseInt(studentId.substring(0, 4));
        String college = user.getCollege(); // college 필드가 User에 있어야 합니다

        return GraduationRequirementUtil.getGraduationCredits(college, admissionYear);
    }

    @Transactional(readOnly = true)
    public List<String> getTakenCourses(Long userId) {
        return getUserCourseSelection(userId).getTakenCourses();
    }

    @Transactional(readOnly = true)
    public List<String> getMandatoryCourses(Long userId) {
        return getUserCourseSelection(userId).getMandatoryCourses();
    }

    @Transactional(readOnly = true)
    public List<String> getRetakeCourses(Long userId) {
        return getUserCourseSelection(userId).getRetakeCourses();
    }

    @Transactional
    public void saveTimePreferences(Long userId, TimePreferenceRequest preferences) {
        logger.info("Saving time preferences for user ID: {}", userId);
        UserPreferenceEntity userPref = getUserPreference(userId);
        userPref.setTimePreferences(preferences != null ? preferences : new TimePreferenceRequest());
        userPreferenceRepository.save(userPref);
        logger.info("Time preferences saved for user ID: {}. Data: {}", userId, preferences);
    }

    @Transactional(readOnly = true)
    public TimePreferenceRequest getTimePreferences(Long userId) {
        UserPreferenceEntity userPref = getUserPreference(userId);
        TimePreferenceRequest prefs = userPref.getTimePreferences();
        return prefs == null ? new TimePreferenceRequest() : prefs;
    }

    @Transactional
    public void saveCreditAndCombinationPreferences(Long userId, CreditSettingsRequest settings) {
        logger.info("Saving credit and combination preferences for user ID: {}", userId);
        UserPreferenceEntity userPref = getUserPreference(userId);
        userPref.setCreditSettings(settings != null ? settings : new CreditSettingsRequest());
        userPreferenceRepository.save(userPref);
        logger.info("Credit and combination preferences saved for user ID: {}. Data: {}", userId, settings);
    }

    @Transactional(readOnly = true)
    public CreditSettingsRequest getCreditAndCombinationPreferences(Long userId) {
        UserPreferenceEntity userPref = getUserPreference(userId);
        CreditSettingsRequest settings = userPref.getCreditSettings();
        return settings == null ? new CreditSettingsRequest() : settings;
    }

    @Transactional
    public void updateTakenCourses(String userId, List<String> courseCodes) {
        UserCourseSelectionEntity selection = getUserCourseSelection(Long.valueOf(userId));
        selection.setTakenCourses(new ArrayList<>(courseCodes));
        userCourseSelectionRepository.save(selection);
    }

    @Transactional
    public void removeTakenCourses(Long userId, List<String> lecturesToRemove) {
        UserCourseSelectionEntity selection = getUserCourseSelection(userId);
        selection.getTakenCourses().removeAll(lecturesToRemove);
        userCourseSelectionRepository.save(selection);
    }

    @Transactional
    public void updateMandatoryCourses(String userId, List<String> courseCodes) {
        UserCourseSelectionEntity selection = getUserCourseSelection(Long.valueOf(userId));
        selection.setMandatoryCourses(new ArrayList<>(courseCodes));
        userCourseSelectionRepository.save(selection);
    }

    @Transactional
    public void removeMandatoryCourses(Long userId, List<String> lecturesToRemove) {
        UserCourseSelectionEntity selection = getUserCourseSelection(userId);
        selection.getMandatoryCourses().removeAll(lecturesToRemove);
        userCourseSelectionRepository.save(selection);
    }

    @Transactional
    public void updateRetakeCourses(String userId, List<String> courseCodes) {
        UserCourseSelectionEntity selection = getUserCourseSelection(Long.valueOf(userId));
        selection.setRetakeCourses(new ArrayList<>(courseCodes));
        userCourseSelectionRepository.save(selection);
    }

    @Transactional
    public void removeRetakeCourses(Long userId, List<String> lecturesToRemove) {
        UserCourseSelectionEntity selection = getUserCourseSelection(userId);
        selection.getRetakeCourses().removeAll(lecturesToRemove);
        userCourseSelectionRepository.save(selection);
    }

    private List<String> removeByIndexes(List<String> original, List<Long> indexes) {
        return original.stream()
                .filter(e -> !indexes.contains((long) original.indexOf(e)))
                .collect(Collectors.toList());
    }

    // userId 조회용
    public UserCourseSelectionEntity getUserCourseSelectionById(Long userId) {
        return userCourseSelectionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + userId));
    }
}