package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.course.PreferencesRequest;
import com.cesco.scheduly.dto.timetable.CreditRangeDto;
import com.cesco.scheduly.dto.timetable.CreditSettingsRequest;
import com.cesco.scheduly.dto.timetable.RecommendedTimetableDto;
import com.cesco.scheduly.dto.timetable.TimePreferenceRequest;
import com.cesco.scheduly.dto.user.MainPageInfoResponse;
import com.cesco.scheduly.dto.user.MyPageResponse;
import com.cesco.scheduly.dto.user.MyPageUpdateRequest;
import com.cesco.scheduly.dto.user.SignupRequest;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.enums.DoubleMajorType;
import com.cesco.scheduly.enums.FusionMajorModule;
import com.cesco.scheduly.exception.AuthenticationException;
import com.cesco.scheduly.exception.ResourceNotFoundException;
import com.cesco.scheduly.exception.UserAlreadyExistsException;
import com.cesco.scheduly.model.DetailedCourseInfo;
import com.cesco.scheduly.repository.UserCourseSelectionRepository;
import com.cesco.scheduly.repository.UserPreferenceRepository;
import com.cesco.scheduly.repository.UserRepository;
import com.cesco.scheduly.util.GraduationRequirementUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserCourseSelectionRepository userCourseSelectionRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final CourseDataService courseDataService;

    private final ObjectMapper objectMapper; // JSON 변환을 위해 ObjectMapper 주입

    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository,
                       UserCourseSelectionRepository userCourseSelectionRepository,
                       UserPreferenceRepository userPreferenceRepository,
                       PasswordEncoder passwordEncoder,
                       CourseDataService courseDataService,
                       ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.userCourseSelectionRepository = userCourseSelectionRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.courseDataService = courseDataService;
    }

    @Transactional
    public User signup(SignupRequest dto) {
        logger.info("Attempting to sign up new user with student ID: {}", dto.getStudentId());
        if (userRepository.existsByStudentId(dto.getStudentId())) {
            logger.warn("Signup failed: Student ID {} already exists.", dto.getStudentId());
            throw new UserAlreadyExistsException();
        }

        // 1. 모듈 정보를 담을 지역 변수 선언 및 null로 초기화
        FusionMajorModule module1 = null;
        FusionMajorModule module2 = null;
        FusionMajorModule module3 = null;

        List<String> modules = dto.getModules(); // DTO에서 모듈 리스트를 가져옴

        if ("융합인재학부".equals(dto.getMajor())) {
            if (modules == null || modules.size() != 3) {
                throw new IllegalArgumentException("융합인재학부(주전공)은 3개의 모듈을 선택해야 합니다.");
            }
            module1 = FusionMajorModule.valueOf(modules.get(0));
            module2 = FusionMajorModule.valueOf(modules.get(1));
            module3 = FusionMajorModule.valueOf(modules.get(2));

        } else if ("융합인재학부".equals(dto.getDoubleMajor())) {
            if (modules == null || modules.size() != 2) {
                throw new IllegalArgumentException("융합인재학부(이중전공)은 2개의 모듈을 선택해야 합니다.");
            }
            module1 = FusionMajorModule.valueOf(modules.get(0));
            module2 = FusionMajorModule.valueOf(modules.get(1));
            // module3 remains null
        }

        // User 엔티티에 @Builder, @NoArgsConstructor, @AllArgsConstructor 어노테이션이 있다고 가정
        // User.java에 @Builder.Default로 createdAt = LocalDateTime.now()가 설정되어 있어야 함
        String doubleMajorType = dto.getDoubleMajorType();
        User user = User.builder()
                .studentId(dto.getStudentId())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .name(dto.getName())
                .major(dto.getMajor())
                .doubleMajor(dto.getDoubleMajor()) // DTO의 필드명 snake_case 유의
                .doubleMajorType(DoubleMajorType.valueOf(dto.getDoubleMajorType())) // 부전공/이중전공/전공심화
                .grade(dto.getGrade())
                .semester(dto.getSemester())
                .college(dto.getCollege())
                .module1(module1)
                .module2(module2)
                .module3(module3)
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
    public MainPageInfoResponse getMainPageInfo(Long userId) {
        User user = getUserDetails(userId);
        return new MainPageInfoResponse(
                user.getName(),
                user.getStudentId(),
                user.getCollege(),
                user.getMajor(),
                user.getDoubleMajorType(),
                user.getDoubleMajor()
        );
    }
    @Transactional
    public void saveTimetable(Long userId, RecommendedTimetableDto timetableDto) {
        UserPreferenceEntity userPref = getUserPreference(userId);
        try {
            String timetableJson = objectMapper.writeValueAsString(timetableDto);
            userPref.setSavedTimetableJson(timetableJson);
            userPreferenceRepository.save(userPref);
            logger.info("User ID {}: 시간표가 성공적으로 저장되었습니다.", userId);
        } catch (JsonProcessingException e) {
            logger.error("User ID {}: 시간표를 JSON으로 변환하는 중 오류 발생", userId, e);
            throw new RuntimeException("시간표 저장 중 오류가 발생했습니다.");
        }
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
        String college = String.valueOf(user.getCollege()); // college 필드가 User에 있어야 합니다

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

        User user = getUserDetails(userId);

        validateCreditSettings(settings, user);

        UserPreferenceEntity userPref = getUserPreference(userId);
        userPref.setCreditSettings(settings != null ? settings : new CreditSettingsRequest());
        userPreferenceRepository.save(userPref);
        logger.info("Credit and combination preferences saved for user ID: {}. Data: {}", userId, settings);
    }

    private void validateCreditSettings(CreditSettingsRequest settings, User user) {
        if (settings.getCreditGoalsPerType() != null && !settings.getCreditGoalsPerType().isEmpty()) {
            DoubleMajorType userMajorType = user.getDoubleMajorType();
            Map<String, CreditRangeDto> goals = settings.getCreditGoalsPerType();

            // 0학점 초과로 설정된 목표 유형만 추출
            Set<String> nonZeroGoalTypes = goals.entrySet().stream()
                    .filter(entry -> entry.getValue().getMax() > 0)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            // 사용자의 전공 유형에 따라 설정 불가능한 '0학점 초과' 목표가 있는지 확인
            switch (userMajorType) {
                case DOUBLE_MAJOR: // 이중전공 사용자
                    if (nonZeroGoalTypes.contains("부전공")) {
                        throw new IllegalArgumentException("'이중전공' 사용자는 '부전공'에 0학점을 초과하여 설정할 수 없습니다.");
                    }
                    break;
                case MINOR: // 부전공 사용자
                    if (nonZeroGoalTypes.contains("이중전공")) {
                        throw new IllegalArgumentException("'부전공' 사용자는 '이중전공'에 0학점을 초과하여 설정할 수 없습니다.");
                    }
                    break;
                case INTENSIVE: // 전공심화 사용자
                    if (nonZeroGoalTypes.contains("이중전공") || nonZeroGoalTypes.contains("부전공")) {
                        throw new IllegalArgumentException("'전공심화' 사용자는 '이중전공' 또는 '부전공'에 0학점을 초과하여 설정할 수 없습니다.");
                    }
                    break;
                case NONE: // 전공이 없는 사용자
                    if  (nonZeroGoalTypes.contains("이중전공") || nonZeroGoalTypes.contains("부전공")) {
                        throw new IllegalArgumentException("'전공 없음' 사용자는 '이중전공', '부전공'에 0학점을 초과하여 설정할 수 없습니다.");
                    }
                    break;
                case INTENSIVE_MINOR: // 전공심화 부전공 사용자
                    if (nonZeroGoalTypes.contains("이중전공")) {
                        throw new IllegalArgumentException("'전공심화 + 부전공' 사용자는 '이중전공'에 0학점을 초과하여 설정할 수 없습니다.");
                    }
                    break;
            }
        }


        // 전체 최소/최대 학점 목표가 없으면 검증할 필요가 없음
        if (settings.getMinTotalCredits() == null || settings.getMaxTotalCredits() == null) {
            return;
        }

        int minTotal = settings.getMinTotalCredits();
        int maxTotal = settings.getMaxTotalCredits();

        // 사용자가 설정한 유형별 학점 목표의 합계를 계산
        int sumOfMinGoals = 0;
        int sumOfMaxGoals = 0;

        if (settings.getCreditGoalsPerType() != null) {
            for (CreditRangeDto range : settings.getCreditGoalsPerType().values()) {
                sumOfMinGoals += range.getMin();
                sumOfMaxGoals += range.getMax();
            }
        }

        // 유효성 검사 로직
        // 1. 유형별 최소 학점의 합계가 전체 최대 학점 목표를 넘을 수 없음
        if (sumOfMinGoals > maxTotal) {
            throw new IllegalArgumentException(
                    "유형별 최소 학점의 합(" + sumOfMinGoals + ")이 전체 최대 학점 목표(" + maxTotal + ")를 초과할 수 없습니다."
            );
        }

        // 2. 유형별 최대 학점의 합계가 전체 최소 학점 목표보다 작을 수 없음
        if (sumOfMaxGoals < minTotal) {
            throw new IllegalArgumentException(
                    "유형별 최대 학점의 합(" + sumOfMaxGoals + ")이 전체 최소 학점 목표(" + minTotal + ")보다 작을 수 없습니다."
            );
        }
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

    private DoubleMajorType parseDoubleMajorType(String type) {
        if (type == null || type.isBlank()) return DoubleMajorType.NONE;
        try {
            return DoubleMajorType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid major type: " + type);
        }
    }

    // userId 조회용
    public UserCourseSelectionEntity getUserCourseSelectionById(Long userId) {
        return userCourseSelectionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + userId));
    }

    @Transactional(readOnly = true)
    public MyPageResponse getMyPageInfo(Long userId) {
        User user = getUserDetails(userId); // 이미 존재하는 메소드

        return new MyPageResponse(
                user.getName(),
                user.getStudentId(),
                user.getCollege(),
                user.getMajor(),
                user.getDoubleMajor(),
                user.getDoubleMajorType(),
                user.getModule1(),
                user.getModule2(),
                user.getModule3(),
                user.getGrade(),
                user.getSemester()
        );
    }

    @Transactional
    public void updateMyPageInfo(Long userId, MyPageUpdateRequest dto) {
        User user = getUserDetails(userId);

        user.setName(dto.getName());
        user.setCollege(dto.getCollege());
        user.setMajor(dto.getMajor());
        user.setDoubleMajor(dto.getDoubleMajor());
        user.setDoubleMajorType(dto.getDoubleMajorType());
        user.setModule1(dto.getModule1());
        user.setModule2(dto.getModule2());
        user.setModule3(dto.getModule3());
        user.setGrade(dto.getGrade());
        user.setSemester(dto.getSemester());

        userRepository.save(user);
    }

    // UserService.java 내부에 추가

    @Transactional(readOnly = true)
    public List<String> getRequiredAndRetakeCourses(Long userId) {
        UserCourseSelectionEntity selection = getUserCourseSelection(userId);
        List<String> combinedList = new ArrayList<>();
        if (selection.getMandatoryCourses() != null) {
            combinedList.addAll(selection.getMandatoryCourses());
        }
        if (selection.getRetakeCourses() != null) {
            combinedList.addAll(selection.getRetakeCourses());
        }
        return combinedList.stream().distinct().collect(Collectors.toList());
    }

    public UserCourseSelectionEntity getUserCourseSelectionByUserId(Long userId) {
        return userCourseSelectionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new NoSuchElementException("선택 과목 정보가 존재하지 않습니다. userId = " + userId));
    }

    public List<DetailedCourseInfo> getTakenCoursesWithDetails(Long userId) {
        List<String> codes = getTakenCourses(userId);
        return codes.stream()
                .map(courseDataService::getDetailedCourseByCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}