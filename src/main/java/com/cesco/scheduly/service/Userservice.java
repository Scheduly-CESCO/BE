package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.SignupRequest;
import com.cesco.scheduly.dto.user.UserRegistrationRequest;
import com.cesco.scheduly.dto.timetable.CreditSettingsRequest;
import com.cesco.scheduly.dto.timetable.TimePreferenceRequest;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserEntity;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.exception.InvalidInputException;
import com.cesco.scheduly.exception.ResourceNotFoundException;
import com.cesco.scheduly.repository.UserCourseSelectionRepository;
import com.cesco.scheduly.repository.UserPreferenceRepository;
import com.cesco.scheduly.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired; // 생성자 주입을 위해 추가
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class Userservice {

    private final UserRepository userRepository;
    private final UserCourseSelectionRepository userCourseSelectionRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final CourseDataService courseDataService; // 학수번호 유효성 검증용 (선택적)


    @Autowired // 생성자 주입 명시
    public Userservice(UserRepository userRepository,
                       UserCourseSelectionRepository userCourseSelectionRepository,
                       UserPreferenceRepository userPreferenceRepository,
                       PasswordEncoder passwordEncoder,
                       CourseDataService courseDataService) { // CourseDataService 주입
        this.userRepository = userRepository;
        this.userCourseSelectionRepository = userCourseSelectionRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.courseDataService = courseDataService;
    }

    @Transactional
    public UserEntity signup(SignupRequest dto) {
        if (userRepository.existsByStudentId(dto.getStudentId())) {
            throw new InvalidInputException("이미 등록된 학번입니다.");
        }

        UserEntity user = new UserEntity();
        user.setStudentId(dto.getStudentId());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setCreatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    @Transactional
    public UserEntity authenticate(String studentId, String password) {
        UserEntity user = userRepository.findByStudentId(studentId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 학번입니다."));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }
        return user;
    }

    public UserEntity registerUser(UserRegistrationRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new InvalidInputException("이미 사용 중인 사용자명입니다: " + request.getUsername());
        }
        UserEntity newUser = UserEntity.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .grade(request.getGrade())
                .major(request.getMajor())
                .doubleMajor(request.getDoubleMajor())
                .build();
        UserEntity savedUser = userRepository.save(newUser);

        // 사용자에 대한 과목 선택 정보 엔티티 생성 및 저장
        UserCourseSelectionEntity selection = UserCourseSelectionEntity.builder()
                .user(savedUser)
                // @Builder.Default로 초기화되므로 명시적으로 new ArrayList<>()를 전달할 필요가 줄어듭니다.
                // 하지만 명시적으로 초기화된 빈 리스트를 설정하는 것도 안전합니다.
                .takenCourses(new ArrayList<>())
                .mandatoryCourses(new ArrayList<>())
                .retakeCourses(new ArrayList<>())
                .build();
        userCourseSelectionRepository.save(selection);

        // 사용자에 대한 선호도 정보 엔티티 생성 및 저장
        UserPreferenceEntity preference = UserPreferenceEntity.builder()
                .user(savedUser)
                // @Builder.Default로 초기화
                .timePreferences(new TimePreferenceRequest()) // 기본 객체로 초기화
                .creditSettings(new CreditSettingsRequest()) // 기본 객체로 초기화
                .build();
        userPreferenceRepository.save(preference);

        return savedUser;
    }

    @Transactional(readOnly = true)
    public UserEntity findUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다. ID: " + userId));
    }

    @Transactional(readOnly = true)
    public UserCourseSelectionEntity getUserCourseSelection(String userId) {
        // findUserById(userId); // 사용자 존재 여부는 findByUser_UserId가 처리 또는 명시적 호출
        return userCourseSelectionRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자 ID " + userId + "에 대한 과목 선택 정보를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public UserPreferenceEntity getUserPreference(String userId) {
        // findUserById(userId);
        return userPreferenceRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("사용자 ID " + userId + "에 대한 환경설정 정보를 찾을 수 없습니다."));
    }

    // 학수번호 유효성 검사 (선택적 기능)
    private void validateCourseCodes(List<String> courseCodes) {
        if (courseCodes == null || courseCodes.isEmpty()) return;
        for (String code : courseCodes) {
            if (courseDataService.getCourseInfoByCode(code) == null && courseDataService.getDetailedCourseByCode(code) == null) {
                // CourseDataService에 해당 학수번호의 강의가 없는 경우
                throw new InvalidInputException("유효하지 않은 학수번호가 포함되어 있습니다: " + code);
            }
        }
    }

    @Transactional
    public void updateTakenCourses(String userId, List<String> courseCodes) {
        // validateCourseCodes(courseCodes); // 선택적 유효성 검사
        UserCourseSelectionEntity selection = getUserCourseSelection(userId);
        selection.setTakenCourses(new ArrayList<>(courseCodes == null ? Collections.emptyList() : courseCodes));
        userCourseSelectionRepository.save(selection);
    }

    @Transactional(readOnly = true)
    public List<String> getTakenCourses(String userId) {
        return getUserCourseSelection(userId).getTakenCourses();
    }

    @Transactional
    public void removeTakenCourses(String userId, List<String> lecturesToRemove) {
        UserCourseSelectionEntity selection = getUserCourseSelection(userId);
        selection.getTakenCourses().removeAll(lecturesToRemove);
        userCourseSelectionRepository.save(selection);
    }

    @Transactional
    public void updateMandatoryCourses(String userId, List<String> courseCodes) {
        // validateCourseCodes(courseCodes);
        UserCourseSelectionEntity selection = getUserCourseSelection(userId);
        selection.setMandatoryCourses(new ArrayList<>(courseCodes == null ? Collections.emptyList() : courseCodes));
        userCourseSelectionRepository.save(selection);
    }

    @Transactional(readOnly = true)
    public List<String> getMandatoryCourses(String userId) {
        return getUserCourseSelection(userId).getMandatoryCourses();
    }

    @Transactional
    public void removeMandatoryCourses(String userId, List<String> lecturesToRemove) {
        UserCourseSelectionEntity selection = getUserCourseSelection(userId);
        selection.getMandatoryCourses().removeAll(lecturesToRemove);
        userCourseSelectionRepository.save(selection);
    }

    @Transactional
    public void updateRetakeCourses(String userId, List<String> courseCodes) {
        UserCourseSelectionEntity selection = getUserCourseSelection(userId);
        List<String> taken = selection.getTakenCourses();
        if (courseCodes != null) {
            for (String code : courseCodes) {
                if (!taken.contains(code)) {
                    // 실제 운영에서는 이 부분에서 예외를 발생시키거나, 프론트엔드에서 먼저 검증하도록 유도
                    System.out.println("경고: 사용자 " + userId + "의 재수강 과목 " + code + "은(는) 수강 이력에 없습니다.");
                    // throw new InvalidInputException("재수강 과목(" + code + ")은 이미 수강한 과목 목록에 있어야 합니다.");
                }
            }
        }
        // validateCourseCodes(courseCodes);
        selection.setRetakeCourses(new ArrayList<>(courseCodes == null ? Collections.emptyList() : courseCodes));
        userCourseSelectionRepository.save(selection);
    }

    @Transactional(readOnly = true)
    public List<String> getRetakeCourses(String userId) {
        return getUserCourseSelection(userId).getRetakeCourses();
    }

    @Transactional
    public void removeRetakeCourses(String userId, List<String> lecturesToRemove) {
        UserCourseSelectionEntity selection = getUserCourseSelection(userId);
        selection.getRetakeCourses().removeAll(lecturesToRemove);
        userCourseSelectionRepository.save(selection);
    }

    @Transactional
    public void saveTimePreferences(String userId, TimePreferenceRequest preferences) {
        UserPreferenceEntity userPref = getUserPreference(userId);
        userPref.setTimePreferences(preferences != null ? preferences : new TimePreferenceRequest());
        userPreferenceRepository.save(userPref);
    }

    @Transactional(readOnly = true)
    public TimePreferenceRequest getTimePreferences(String userId) {
        TimePreferenceRequest prefs = getUserPreference(userId).getTimePreferences();
        return prefs == null ? new TimePreferenceRequest() : prefs;
    }

    @Transactional
    public void saveCreditAndCombinationPreferences(String userId, CreditSettingsRequest settings) {
        UserPreferenceEntity userPref = getUserPreference(userId);
        userPref.setCreditSettings(settings != null ? settings : new CreditSettingsRequest());
        userPreferenceRepository.save(userPref);
    }

    @Transactional(readOnly = true)
    public CreditSettingsRequest getCreditAndCombinationPreferences(String userId) {
        CreditSettingsRequest settings = getUserPreference(userId).getCreditSettings();
        return settings == null ? new CreditSettingsRequest() : settings;
    }

    @Transactional(readOnly = true)
    public UserEntity getUserDetails(String userId) {
        return findUserById(userId);
    }
}