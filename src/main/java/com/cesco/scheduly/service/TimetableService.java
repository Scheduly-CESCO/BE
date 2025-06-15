package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.timetable.*;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.enums.DoubleMajorType;
import com.cesco.scheduly.exception.MandatoryCourseConflictException;
import com.cesco.scheduly.model.DetailedCourseInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimetableService {

    private static final Logger logger = LoggerFactory.getLogger(TimetableService.class);

    private final UserService userService;
    private final CourseDataService courseDataService;
    private final ObjectMapper objectMapper; // JSON 변환을 위해 ObjectMapper 주입

    private static final int MAX_RECOMMENDATIONS = 5; // 생성할 최대 추천 시간표 개수

    @Autowired
    public TimetableService(UserService userService, CourseDataService courseDataService, ObjectMapper objectMapper) {
        this.userService = userService;
        this.courseDataService = courseDataService;
        this.objectMapper = objectMapper; // ObjectMapper 초기화
    }

    public MainPageTimetableResponse getMainPageTimetable(Long userId) {
        UserPreferenceEntity userPreferences = userService.getUserPreference(userId);
        String savedTimetableJson = userPreferences.getSavedTimetableJson();

        // 1. 저장된 시간표가 있는지 확인
        if (savedTimetableJson != null && !savedTimetableJson.isEmpty()) {
            try {
                // 2. JSON 문자열을 RecommendedTimetableDto 객체로 변환
                RecommendedTimetableDto savedTimetable = objectMapper.readValue(savedTimetableJson, RecommendedTimetableDto.class);
                logger.info("User ID {}: 저장된 시간표를 불러옵니다.", userId);
                return new MainPageTimetableResponse(true, savedTimetable, "저장된 시간표입니다.");
            } catch (JsonProcessingException e) {
                logger.error("User ID {}: 저장된 시간표 JSON 파싱 중 오류 발생", userId, e);
                // 파싱 실패 시, 시간표가 없는 것처럼 처리
            }
        }

        // 3. 저장된 시간표가 없으면, 없다는 메시지 반환
        logger.info("User ID {}: 저장된 시간표가 없습니다.", userId);
        return new MainPageTimetableResponse(false, null, "아직 생성된 시간표가 없어요! 시간표를 생성하러 가볼까요?");
    }

    public List<RecommendedTimetableDto> generateRecommendations(Long userId) {
        logger.info("User ID {} 시간표 추천 생성 시작", userId);
        User currentUser = userService.getUserDetails(userId);
        UserCourseSelectionEntity userSelections = userService.getUserCourseSelection(userId);
        UserPreferenceEntity userPreferences = userService.getUserPreference(userId);

        TimePreferenceRequest timePreferences = Optional.ofNullable(userPreferences.getTimePreferences()).orElseGet(TimePreferenceRequest::new);
        CreditSettingsRequest creditSettings = Optional.ofNullable(userPreferences.getCreditSettings()).orElseGet(CreditSettingsRequest::new);

        List<String> targetCourseTypes = creditSettings.getCreditGoalsPerType() != null ?
                new ArrayList<>(creditSettings.getCreditGoalsPerType().keySet()) : Collections.emptyList();

        logger.debug("User ID {}: 사용자 정보(학년 {}), 목표 학점 유형: {}", userId, currentUser.getGrade(), targetCourseTypes);

        List<DetailedCourseInfo> allCourses = courseDataService.getDetailedCourses();
        if (allCourses.isEmpty()) {
            logger.warn("User ID {}: 로드된 강의 데이터가 없습니다.", userId);
            return Collections.emptyList();
        }

        List<DetailedCourseInfo> candidatePool = prepareCandidateCourses(allCourses, userSelections);
        logger.debug("User ID {}: 기수강/재수강 필터 후 후보 강의 수: {}", userId, candidatePool.size());

        List<DetailedCourseInfo> mandatoryScheduledCourses = getAndValidateMandatoryCourses(candidatePool, userSelections, currentUser);
        logger.info("User ID {}: 필수/재수강 과목 처리 완료 ({}개)", userId, mandatoryScheduledCourses.size());

        List<List<DetailedCourseInfo>> generatedRawTimetables = findTimetableCombinations(
                mandatoryScheduledCourses, candidatePool,
                timePreferences, creditSettings, MAX_RECOMMENDATIONS, currentUser, targetCourseTypes
        );
        logger.info("User ID {}: {}개의 원시 시간표 조합 생성됨.", userId, generatedRawTimetables.size());

        List<RecommendedTimetableDto> recommendations = new ArrayList<>();
        for (int i = 0; i < generatedRawTimetables.size(); i++) {
            List<DetailedCourseInfo> timetableCourses = generatedRawTimetables.get(i);
            Map<String, Integer> creditsByType = calculateCreditsByTypeForUser(timetableCourses, currentUser, creditSettings);
            int totalCredits = creditsByType.values().stream().mapToInt(Integer::intValue).sum();
            recommendations.add(convertToRecommendedDtoForUser(i + 1, timetableCourses, creditsByType, totalCredits, currentUser, creditSettings));
        }

        // 필수 과목만으로도 조건 충족 시 추천 목록에 추가
        if (recommendations.isEmpty() && !mandatoryScheduledCourses.isEmpty()) {
            if (meetsAllCreditCriteria(mandatoryScheduledCourses, creditSettings, currentUser)) {
                Map<String, Integer> mandatoryCreditsByType = calculateCreditsByTypeForUser(mandatoryScheduledCourses, currentUser, creditSettings);
                int mandatoryTotalCredits = mandatoryCreditsByType.values().stream().mapToInt(Integer::intValue).sum();
                recommendations.add(convertToRecommendedDtoForUser(0, mandatoryScheduledCourses, mandatoryCreditsByType, mandatoryTotalCredits, currentUser, creditSettings));
                logger.info("User ID {}: 필수 과목만으로 구성된 시간표를 추천합니다.", userId);
            }
        }

        if (recommendations.isEmpty()) {
            logger.warn("User ID {}: 최종 추천 시간표를 생성하지 못했습니다.", userId);
        } else {
            logger.info("User ID {}: 최종 {}개의 시간표 추천.", userId, recommendations.size());
        }

        return recommendations;
    }

    private List<DetailedCourseInfo> prepareCandidateCourses(List<DetailedCourseInfo> allCourses, UserCourseSelectionEntity selections) {
        Set<String> takenGroupIds = selections.getTakenCourses().stream()
                .map(courseDataService::getDetailedCourseByCode)
                .filter(Objects::nonNull)
                .map(c -> c.getGroupId() != null ? c.getGroupId() : c.getCourseCode())
                .collect(Collectors.toSet());

        Set<String> retakeCodes = new HashSet<>(selections.getRetakeCourses());

        return allCourses.stream()
                .filter(course -> {
                    String identifier = course.getGroupId() != null ? course.getGroupId() : course.getCourseCode();
                    return retakeCodes.contains(course.getCourseCode()) || !takenGroupIds.contains(identifier);
                })
                .collect(Collectors.toList());
    }

    private List<DetailedCourseInfo> getAndValidateMandatoryCourses(List<DetailedCourseInfo> candidatePool,
                                                                    UserCourseSelectionEntity selections,
                                                                    User currentUser) {
        Set<String> mandatoryCodes = new HashSet<>(selections.getMandatoryCourses());
        mandatoryCodes.addAll(selections.getRetakeCourses());

        if (mandatoryCodes.isEmpty()) {
            return Collections.emptyList();
        }

        List<DetailedCourseInfo> mandatoryCourses = candidatePool.stream()
                .filter(c -> mandatoryCodes.contains(c.getCourseCode()))
                .collect(Collectors.toMap(
                        c -> c.getGroupId() != null ? c.getGroupId() : c.getCourseCode(),
                        c -> c,
                        (existing, replacement) -> existing
                )).values().stream().toList();

        if (hasTimeConflictInList(mandatoryCourses)) {
            String conflictingCourses = mandatoryCourses.stream()
                    .map(c -> c.getCourseName() + "(" + c.getCourseCode() + ")")
                    .collect(Collectors.joining(", "));
            logger.error("User ID {}: 필수/재수강 과목 간 시간 중복 발생: {}", currentUser.getId(), conflictingCourses);
            throw new MandatoryCourseConflictException("필수/재수강 과목 간 시간이 중복됩니다: " + conflictingCourses);
        }
        return mandatoryCourses;
    }

// ================== 핵심 수정 영역: 분할 정복 알고리즘 (최종 수정안) ==================


    private List<List<DetailedCourseInfo>> findTimetableCombinations(

            List<DetailedCourseInfo> initialTimetableBase,
            List<DetailedCourseInfo> availableCoursePool,
            TimePreferenceRequest timePreferences,
            CreditSettingsRequest creditSettings,
            int numRecommendationsNeeded,
            User currentUser,
            List<String> targetCourseTypes) {

        List<DetailedCourseInfo> timeFilteredPool = filterByTimePreferences(availableCoursePool, timePreferences);

        Set<String> initialCourseIdentifiers = initialTimetableBase.stream()
                .map(c -> c.getGroupId() != null ? c.getGroupId() : c.getCourseCode())
                .collect(Collectors.toSet());

        Map<String, List<DetailedCourseInfo>> electivesByType = timeFilteredPool.stream()
                .filter(c -> {
                    String identifier = c.getGroupId() != null ? c.getGroupId() : c.getCourseCode();
                    return !initialCourseIdentifiers.contains(identifier);
                })
                .collect(Collectors.groupingBy(course -> getActualCourseTypeForUser(course, currentUser, creditSettings)));

        List<List<DetailedCourseInfo>> finalTimetables = new ArrayList<>();

        // 재귀 탐색 시작
        generateCombinationsRecursive(
                targetCourseTypes, 0, initialTimetableBase, electivesByType,
                creditSettings, finalTimetables, numRecommendationsNeeded, currentUser
        );


        return finalTimetables;
    }

    private void generateCombinationsRecursive(List<String> targetTypes,
                                               int typeIndex,
                                               List<DetailedCourseInfo> currentTimetable,
                                               Map<String, List<DetailedCourseInfo>> electivesByType,
                                               CreditSettingsRequest creditSettings,
                                               List<List<DetailedCourseInfo>> finalResult,
                                               int numRecommendationsNeeded,
                                               User currentUser) {

        if (finalResult.size() >= numRecommendationsNeeded) {
            return;
        }

        if (typeIndex >= targetTypes.size()) {
            if (meetsAllCreditCriteria(currentTimetable, creditSettings, currentUser)) {
                finalResult.add(new ArrayList<>(currentTimetable));
            }
            return;
        }

        String currentType = targetTypes.get(typeIndex);
        CreditRangeDto originalRange = creditSettings.getCreditGoalsPerType().get(currentType);

        if (originalRange == null) {
            generateCombinationsRecursive(targetTypes, typeIndex + 1, currentTimetable, electivesByType, creditSettings, finalResult, numRecommendationsNeeded, currentUser);
            return;
        }

        Map<String, Integer> currentCreditsByType = calculateCreditsByTypeForUser(currentTimetable, currentUser, creditSettings);
        int initialCredits = currentCreditsByType.getOrDefault(currentType, 0);
        int newMin = Math.max(0, originalRange.getMin() - initialCredits);
        int newMax = originalRange.getMax() - initialCredits;

        List<DetailedCourseInfo> typePool = electivesByType.getOrDefault(currentType, Collections.emptyList());

        // 현재 유형에서 추가할 수 있는 모든 부분 조합을 찾음
        // ★ 핵심: 헬퍼 함수는 "가능한 모든 조합"을 찾는 역할만 충실히 수행
        List<List<DetailedCourseInfo>> partialCombinations = findPartialCombinations(typePool, new CreditRangeDto(newMin, newMax));

        // ★ 핵심: 만약 가능한 조합이 없다면, 이 경로는 더 이상 진행할 수 없으므로 종료(백트래킹)
        // 단, newMin이 0이었다면 partialCombinations에 '빈 리스트'가 포함되어 있으므로 이 조건에 걸리지 않음.
        if (partialCombinations.isEmpty()){
            return;
        }

        // 각 부분 조합에 대해 재귀적으로 다음 단계 진행
        for (List<DetailedCourseInfo> partial : partialCombinations) {
            if (finalResult.size() >= numRecommendationsNeeded) return;

            List<DetailedCourseInfo> nextTimetable = new ArrayList<>(currentTimetable);
            nextTimetable.addAll(partial);

            if (!hasTimeConflictInList(nextTimetable)) {
                generateCombinationsRecursive(targetTypes, typeIndex + 1, nextTimetable, electivesByType, creditSettings, finalResult, numRecommendationsNeeded, currentUser);
            }
        }
    }

    // 이 아래 두 헬퍼 메서드는 이전과 동일하며, 수정할 필요가 없습니다.
    private List<List<DetailedCourseInfo>> findPartialCombinations(List<DetailedCourseInfo> pool, CreditRangeDto range) {
        List<List<DetailedCourseInfo>> result = new ArrayList<>();
        findPartialCombinationsRecursive(pool, range, 0, new ArrayList<>(), 0, result);
        // ★ 핵심: newMin=0일 때, 아래 재귀함수가 시작점에서 바로 빈 리스트 '[]'를 결과에 추가해줌
        if (range.getMin() == 0 && result.stream().noneMatch(List::isEmpty)) {
            result.add(new ArrayList<>());
        }
        return result;
    }

    private void findPartialCombinationsRecursive(List<DetailedCourseInfo> pool, CreditRangeDto range, int startIndex,
                                                  List<DetailedCourseInfo> currentCombination, int currentCredits,
                                                  List<List<DetailedCourseInfo>> result) {

        if (currentCredits >= range.getMin() && currentCredits <= range.getMax()) {
            result.add(new ArrayList<>(currentCombination));
        }

        if (startIndex >= pool.size() || currentCredits >= range.getMax()) {
            return;
        }

        for (int i = startIndex; i < pool.size(); i++) {
            DetailedCourseInfo courseToAdd = pool.get(i);
            if (currentCredits + courseToAdd.getCredits() > range.getMax()) continue;

            String courseIdentifier = courseToAdd.getGroupId() != null ? courseToAdd.getGroupId() : courseToAdd.getCourseCode();
            boolean isDuplicate = currentCombination.stream().anyMatch(c ->
                    (c.getGroupId() != null ? c.getGroupId() : c.getCourseCode()).equals(courseIdentifier));
            if (isDuplicate) continue;

            currentCombination.add(courseToAdd);
            if (!hasTimeConflictInList(currentCombination)) {
                findPartialCombinationsRecursive(pool, range, i + 1, currentCombination, currentCredits + courseToAdd.getCredits(), result);
            }
            currentCombination.remove(currentCombination.size() - 1);
        }
    }




    // ================== 유틸리티 및 헬퍼 메서드 ==================

    private boolean meetsAllCreditCriteria(List<DetailedCourseInfo> timetable, CreditSettingsRequest settings, User user) {
        if (timetable.isEmpty()) {
            return !(settings.getMinTotalCredits() != null && settings.getMinTotalCredits() > 0);
        }

        Map<String, Integer> creditsByType = calculateCreditsByTypeForUser(timetable, user, settings);
        int totalCredits = creditsByType.values().stream().mapToInt(Integer::intValue).sum();

        if (settings.getMinTotalCredits() != null && totalCredits < settings.getMinTotalCredits()) return false;
        if (settings.getMaxTotalCredits() != null && totalCredits > settings.getMaxTotalCredits()) return false;

        if (settings.getCreditGoalsPerType() != null) {
            for (Map.Entry<String, CreditRangeDto> entry : settings.getCreditGoalsPerType().entrySet()) {
                String type = entry.getKey();
                CreditRangeDto range = entry.getValue();
                int creditsForType = creditsByType.getOrDefault(type, 0);
                if (creditsForType < range.getMin() || creditsForType > range.getMax()) return false;
            }
        }
        return true;
    }

    private Map<String, Integer> calculateCreditsByTypeForUser(List<DetailedCourseInfo> courses, User currentUser, CreditSettingsRequest creditSettings) {
        Map<String, Integer> creditsMap = new HashMap<>();
        if (creditSettings.getCreditGoalsPerType() != null) {
            creditSettings.getCreditGoalsPerType().keySet().forEach(type -> creditsMap.put(type, 0));
        }

        for (DetailedCourseInfo course : courses) {
            String actualCourseType = getActualCourseTypeForUser(course, currentUser, creditSettings);
            creditsMap.put(actualCourseType, creditsMap.getOrDefault(actualCourseType, 0) + course.getCredits());
        }
        return creditsMap;
    }

    private List<DetailedCourseInfo> filterByTimePreferences(List<DetailedCourseInfo> courses, TimePreferenceRequest preferences) {
        if (preferences == null || preferences.getPreferredTimeSlots() == null || preferences.getPreferredTimeSlots().isEmpty()) {
            return courses;
        }

        Map<String, Set<Integer>> allowedSlotsMap = new HashMap<>();
        for (TimeSlotDto preferredSlot : preferences.getPreferredTimeSlots()) {
            allowedSlotsMap
                    .computeIfAbsent(preferredSlot.getDay(), k -> new HashSet<>())
                    .addAll(preferredSlot.getPeriods());
        }

        return courses.stream().filter(course -> {
            if (course.getScheduleSlots() == null || course.getScheduleSlots().isEmpty()) return false;
            for (TimeSlotDto courseSlot : course.getScheduleSlots()) {
                Set<Integer> allowedPeriods = allowedSlotsMap.get(courseSlot.getDay());
                if (allowedPeriods == null || !allowedPeriods.containsAll(courseSlot.getPeriods())) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());
    }

    // TimetableService.java 내
    private boolean hasTimeConflictInList(List<DetailedCourseInfo> courses) {
        if (courses == null || courses.size() < 2) {
            return false;
        }
        // 요일별로 수업 교시를 저장할 맵
        Map<String, BitSet> schedule = new HashMap<>();

        for (DetailedCourseInfo course : courses) {
            if (course.getScheduleSlots() == null || course.getScheduleSlots().isEmpty()) {
                continue;
            }
            for (TimeSlotDto slot : course.getScheduleSlots()) {
                // 해당 요일의 BitSet을 가져오거나 새로 생성
                BitSet daySchedule = schedule.computeIfAbsent(slot.getDay(), k -> new BitSet(16)); // 1~15교시 사용 가정

                for (int period : slot.getPeriods()) {
                    // 해당 교시에 이미 수업이 있는지(bit가 1인지) 확인
                    if (daySchedule.get(period)) {
                        // 이미 수업이 있다면 충돌 발생
                        logger.error("시간표 충돌 발생: {} 과목의 {} 교시가 이미 예약되어 있습니다.", course.getCourseName(), period);
                        return true;
                    }
                    // 수업이 없다면 해당 교시의 bit를 1로 설정
                    daySchedule.set(period);
                }
            }
        }
        // 모든 과목을 확인했는데 충돌이 없으면 false 반환
        return false;
    }

    private String getActualCourseTypeForUser(DetailedCourseInfo course, User currentUser, CreditSettingsRequest creditSettings) {
        if (course == null || currentUser == null) {
            logger.warn("getActualCourseTypeForUser 호출 시 course 또는 currentUser가 null입니다.");
            return "기타";
        }

        String primaryMajorName = currentUser.getMajor();
        String secondaryMajorName = currentUser.getDoubleMajor();
        DoubleMajorType userMajorType = currentUser.getDoubleMajorType();
        String courseSpecificMajor = course.getSpecificMajor();
        String courseGeneralizedType = course.getGeneralizedType();

        // 1. 세부전공 정보가 있을 경우, 사용자의 전공 정보와 먼저 비교
        if (courseSpecificMajor != null && !courseSpecificMajor.isBlank()) {
            // 1-1. 주전공 일치 여부
            if (courseSpecificMajor.equalsIgnoreCase(primaryMajorName)) {
                return "전공";
            }
            // 1-2. 이중/부전공 일치 여부
            if (courseSpecificMajor.equalsIgnoreCase(secondaryMajorName)) {
                if (userMajorType == DoubleMajorType.DOUBLE_MAJOR) return "이중전공";
                if (userMajorType == DoubleMajorType.MINOR || userMajorType == DoubleMajorType.INTENSIVE_MINOR) return "부전공";
            }
        }

        // 2. 세부전공으로 판단되지 않은 과목들을 대상으로 1차 분류된 generalizedType을 확인
        if (Objects.equals(courseGeneralizedType, "전공_후보")) {
            // 주전공/이중/부전공에 해당하지 않는 다른 학과의 전공 과목이므로 '자선'으로 판별
            return "자선";
        }

        // 3. 교양 및 기타 유형 처리
        if (courseGeneralizedType != null) {
            return courseGeneralizedType; // "교양", "교직" 등
        }

        return "기타"; // 모든 조건에 해당하지 않는 경우
    }

    private RecommendedTimetableDto convertToRecommendedDtoForUser(int id, List<DetailedCourseInfo> courses, Map<String, Integer> creditsByType, int totalCredits, User currentUser, CreditSettingsRequest creditSettings) {
        List<ScheduledCourseDto> scheduledCourses = courses.stream()
                .map(course -> new ScheduledCourseDto(
                        course.getCourseCode(),
                        course.getCourseName(),
                        getActualCourseTypeForUser(course, currentUser, creditSettings),
                        course.getCredits(),
                        course.getProfessor(),
                        course.getClassroom(),
                        course.getRemarks(),
                        course.getScheduleSlots()
                ))
                .collect(Collectors.toList());
        return new RecommendedTimetableDto(id, scheduledCourses, creditsByType, totalCredits);
    }
}