package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.timetable.*;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.UserPreferenceEntity;
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

    // ================== 핵심 수정 영역: 분할 정복 알고리즘 ==================

    private List<List<DetailedCourseInfo>> findTimetableCombinations(
            List<DetailedCourseInfo> initialTimetableBase,
            List<DetailedCourseInfo> availableCoursePool,
            TimePreferenceRequest timePreferences,
            CreditSettingsRequest creditSettings,
            int numRecommendationsNeeded,
            User currentUser,
            List<String> targetCourseTypes) {

        logger.debug("User ID {}: 조합 탐색 시작. 초기 과목 {}개.", currentUser.getId(), initialTimetableBase.size());

        // 1. [최적화] 가장 큰 풀에 대해 선호도 필터링을 '먼저', '한 번만' 수행
        List<DetailedCourseInfo> timeFilteredPool = filterByTimePreferences(availableCoursePool, timePreferences);
        logger.debug("User ID {}: 선호 시간 필터링 후 후보 강의 수: {}", currentUser.getId(), timeFilteredPool.size());

        Set<String> initialCourseIdentifiers = initialTimetableBase.stream()
                .map(c -> c.getGroupId() != null ? c.getGroupId() : c.getCourseCode())
                .collect(Collectors.toSet());

        // 2. [최적화] 대폭 줄어든 풀을 기반으로, 유형별 선택과목 풀 생성
        Map<String, List<DetailedCourseInfo>> electivesByType = timeFilteredPool.stream()
                .filter(c -> {
                    // 필수/재수강으로 이미 선택된 과목 및 동일 그룹 과목 제외
                    String identifier = c.getGroupId() != null ? c.getGroupId() : c.getCourseCode();
                    return !initialCourseIdentifiers.contains(identifier);
                })
                .collect(Collectors.groupingBy(course -> getActualCourseTypeForUser(course, currentUser, creditSettings)));

        // 3. [분할] 각 목표 유형별로 학점 조건을 만족하는 '부분 조합' 리스트를 미리 계산
        Map<String, List<List<DetailedCourseInfo>>> combinationsByType = new HashMap<>();
        for (String type : targetCourseTypes) {
            CreditRangeDto range = creditSettings.getCreditGoalsPerType().get(type);
            if (range == null || range.getMax() <= 0) continue;

            List<DetailedCourseInfo> typePool = electivesByType.getOrDefault(type, Collections.emptyList());
            List<List<DetailedCourseInfo>> typeCombinations = findCombinationsForType(typePool, range);

            // 해당 유형의 조합이 필수적인데 생성되지 않았다면, 더 이상 진행 불가
            if (range.getMin() > 0 && typeCombinations.isEmpty()) {
                logger.warn("User ID {}: 필수 요건인 '{}' 유형 (최소 {}학점) 과목 조합을 생성할 수 없어 시간표 추천이 불가능합니다.", currentUser.getId(), type, range.getMin());
                return Collections.emptyList();
            }
            combinationsByType.put(type, typeCombinations);
        }

        // 4. [정복] 생성된 부분 조합들을 재귀적으로 결합하여 최종 시간표 완성
        List<List<DetailedCourseInfo>> finalTimetables = new ArrayList<>();
        combineTypeCombinationsRecursive(
                targetCourseTypes, 0, initialTimetableBase, combinationsByType,
                finalTimetables, creditSettings, numRecommendationsNeeded, currentUser);

        return finalTimetables;
    }

    private List<List<DetailedCourseInfo>> findCombinationsForType(List<DetailedCourseInfo> typePool, CreditRangeDto range) {
        List<List<DetailedCourseInfo>> result = new ArrayList<>();
        findCombinationsForTypeRecursive(typePool, range, 0, new ArrayList<>(), 0, result);
        return result;
    }

    private void findCombinationsForTypeRecursive(List<DetailedCourseInfo> pool, CreditRangeDto range, int startIndex,
                                                  List<DetailedCourseInfo> currentCombination, int currentCredits, List<List<DetailedCourseInfo>> result) {
        if (currentCredits >= range.getMin() && currentCredits <= range.getMax()) {
            result.add(new ArrayList<>(currentCombination));
        }

        if (startIndex >= pool.size() || currentCredits >= range.getMax()) {
            return;
        }

        for (int i = startIndex; i < pool.size(); i++) {
            DetailedCourseInfo courseToAdd = pool.get(i);
            if (currentCredits + courseToAdd.getCredits() > range.getMax()) continue;

            currentCombination.add(courseToAdd);
            if (!hasTimeConflictInList(currentCombination)) {
                findCombinationsForTypeRecursive(pool, range, i + 1, currentCombination, currentCredits + courseToAdd.getCredits(), result);
            }
            currentCombination.remove(currentCombination.size() - 1);
        }
    }

    private void combineTypeCombinationsRecursive(List<String> targetTypes, int typeIndex,
                                                  List<DetailedCourseInfo> currentTimetable,
                                                  Map<String, List<List<DetailedCourseInfo>>> combinationsByType,
                                                  List<List<DetailedCourseInfo>> finalResult,
                                                  CreditSettingsRequest creditSettings, int numRecommendationsNeeded,
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
        List<List<DetailedCourseInfo>> partialCombinations = combinationsByType.get(currentType);

        // 해당 유형의 조합이 없거나 필수적이지 않다면(min=0), 바로 다음 유형으로 넘어감
        CreditRangeDto range = creditSettings.getCreditGoalsPerType().get(currentType);
        if (partialCombinations == null || partialCombinations.isEmpty()) {
            if (range != null && range.getMin() == 0) {
                combineTypeCombinationsRecursive(targetTypes, typeIndex + 1, currentTimetable, combinationsByType, finalResult, creditSettings, numRecommendationsNeeded, currentUser);
            }
            return;
        }

        for (List<DetailedCourseInfo> partial : partialCombinations) {
            if (finalResult.size() >= numRecommendationsNeeded) return;

            List<DetailedCourseInfo> nextTimetable = new ArrayList<>(currentTimetable);
            nextTimetable.addAll(partial);
            if (!hasTimeConflictInList(nextTimetable)) {
                combineTypeCombinationsRecursive(targetTypes, typeIndex + 1, nextTimetable, combinationsByType, finalResult, creditSettings, numRecommendationsNeeded, currentUser);
            }
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

    private boolean hasTimeConflictInList(List<DetailedCourseInfo> courses) {
        if (courses.size() < 2) return false;
        Map<String, BitSet> schedule = new HashMap<>();
        for (DetailedCourseInfo course : courses) {
            if (course.getScheduleSlots() == null) continue;
            for (TimeSlotDto slot : course.getScheduleSlots()) {
                BitSet daySchedule = schedule.computeIfAbsent(slot.getDay(), k -> new BitSet(16));
                for (int period : slot.getPeriods()) {
                    if (daySchedule.get(period)) {
                        return true;
                    }
                    daySchedule.set(period);
                }
            }
        }
        return false;
    }

    private String getActualCourseTypeForUser(DetailedCourseInfo course, User currentUser, CreditSettingsRequest creditSettings) {
        if (course == null || currentUser == null) {
            return "기타";
        }
        String primaryMajorName = currentUser.getMajor();
        String secondaryMajorName = currentUser.getDoubleMajor();
        String courseSpecificMajor = course.getSpecificMajor();
        String courseDeptOriginal = course.getDepartmentOriginal();
        String courseGeneralizedType = course.getGeneralizedType();
        if (courseGeneralizedType == null) {
            courseGeneralizedType = courseDataService.determineInitialGeneralizedType(courseDeptOriginal);
        }

        if (secondaryMajorName != null && !secondaryMajorName.isBlank() &&
                courseSpecificMajor != null && courseSpecificMajor.equalsIgnoreCase(secondaryMajorName)) {
            return "이중전공";
        }
        if (primaryMajorName != null && !primaryMajorName.isBlank() &&
                courseSpecificMajor != null && courseSpecificMajor.equalsIgnoreCase(primaryMajorName)) {
            return "전공";
        }
        if (courseDeptOriginal != null && courseDeptOriginal.equalsIgnoreCase("전공")) {
            return "자선";
        }
        if (Objects.equals(courseGeneralizedType, "전공_후보")) {
            if (primaryMajorName != null && !primaryMajorName.isBlank() && courseDeptOriginal != null && courseDeptOriginal.toLowerCase().contains(primaryMajorName.toLowerCase())) {
                return "전공";
            }
            if (secondaryMajorName != null && !secondaryMajorName.isBlank() && courseDeptOriginal != null && courseDeptOriginal.toLowerCase().contains(secondaryMajorName.toLowerCase())) {
                return "이중전공";
            }
            return "일반선택";
        }

        if(courseGeneralizedType != null && courseGeneralizedType.equals("교양")){
            return "교양";
        }

        return "일반선택";
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
