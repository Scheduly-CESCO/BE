package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.timetable.*;
import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.exception.MandatoryCourseConflictException;
import com.cesco.scheduly.model.DetailedCourseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimetableService {

    private static final Logger logger = LoggerFactory.getLogger(TimetableService.class);

    private final UserService userService; // UserService는 User 엔티티를 반환하도록 수정되었다고 가정
    private final CourseDataService courseDataService;

    @Autowired
    public TimetableService(UserService userService, CourseDataService courseDataService) {
        this.userService = userService;
        this.courseDataService = courseDataService;
    }

    // 사용자별로 과목의 실제 유형("전공", "이중전공", "교양", "자선" 등)을 최종 판단
    private String getActualCourseTypeForUser(DetailedCourseInfo course, User currentUser, CreditSettingsRequest creditSettings) {
        if (course == null || currentUser == null) {
            logger.warn("getActualCourseTypeForUser 호출 시 course 또는 currentUser가 null입니다.");
            return "기타";
        }

        String primaryMajorName = currentUser.getMajor(); // User 클래스의 getter 사용
        String secondaryMajorName = currentUser.getDoubleMajor(); // User 클래스의 getter 사용
        String courseSpecificMajor = course.getSpecificMajor();
        String courseDeptOriginal = course.getDepartmentOriginal();
        String courseGeneralizedType = course.getGeneralizedType();
        if (courseGeneralizedType == null) {
            courseGeneralizedType = courseDataService.determineInitialGeneralizedType(courseDeptOriginal);
        }

        // 1. 이중전공 판단
        if (secondaryMajorName != null && !secondaryMajorName.isBlank() &&
                courseSpecificMajor != null && !courseSpecificMajor.isBlank() &&
                courseSpecificMajor.equalsIgnoreCase(secondaryMajorName)) {
            return "이중전공";
        }

        // 2. 주전공 판단
        if (primaryMajorName != null && !primaryMajorName.isBlank() &&
                courseSpecificMajor != null && !courseSpecificMajor.isBlank() &&
                courseSpecificMajor.equalsIgnoreCase(primaryMajorName)) {
            return "전공";
        }

        // 3. "자선" 과목 판단
        if (courseDeptOriginal != null && courseDeptOriginal.equalsIgnoreCase("전공")) {
            return "자선";
        }

        // 4. 그 외
        if (Objects.equals(courseGeneralizedType, "전공_후보")) {
            if (primaryMajorName != null && !primaryMajorName.isBlank() &&
                    courseDeptOriginal != null && courseDeptOriginal.toLowerCase().contains(primaryMajorName.toLowerCase())) {
                return "전공";
            }
            if (secondaryMajorName != null && !secondaryMajorName.isBlank() &&
                    courseDeptOriginal != null && courseDeptOriginal.toLowerCase().contains(secondaryMajorName.toLowerCase())) {
                return "이중전공";
            }
            return "일반선택";
        }

        if (courseGeneralizedType != null &&
                !courseGeneralizedType.equals("미분류_전공성") &&
                !courseGeneralizedType.equals("기타")) {
            return courseGeneralizedType;
        }

        return "기타";
    }

    private List<DetailedCourseInfo> prepareCandidateCourses(List<DetailedCourseInfo> allCourses,
                                                             UserCourseSelectionEntity selections,
                                                             int userGrade, // User 엔티티의 grade는 int
                                                             User currentUser,
                                                             CreditSettingsRequest creditSettings) {
        Set<String> takenCourseGroupIds = new HashSet<>();
        Set<String> retakeCourseGroupIds = new HashSet<>();

        if (selections.getRetakeCourses() != null) {
            for (String retakeCode : selections.getRetakeCourses()) {
                DetailedCourseInfo retakeCourse = courseDataService.getDetailedCourseByCode(retakeCode);
                if (retakeCourse != null && retakeCourse.getGroupId() != null) {
                    retakeCourseGroupIds.add(retakeCourse.getGroupId());
                } else if (retakeCourse != null) {
                    retakeCourseGroupIds.add(retakeCourse.getCourseCode());
                }
            }
        }

        if (selections.getTakenCourses() != null) {
            for (String takenCode : selections.getTakenCourses()) {
                DetailedCourseInfo takenCourse = courseDataService.getDetailedCourseByCode(takenCode);
                if (takenCourse != null && takenCourse.getGroupId() != null) {
                    if (!retakeCourseGroupIds.contains(takenCourse.getGroupId())) {
                        takenCourseGroupIds.add(takenCourse.getGroupId());
                    }
                } else if (takenCourse != null) {
                    if (!retakeCourseGroupIds.contains(takenCourse.getCourseCode())) {
                        takenCourseGroupIds.add(takenCourse.getCourseCode());
                    }
                }
            }
        }

        return allCourses.stream()
                .filter(course -> {
                    String currentCourseIdentifier = course.getGroupId() != null ? course.getGroupId() : course.getCourseCode();
                    return !takenCourseGroupIds.contains(currentCourseIdentifier) || retakeCourseGroupIds.contains(currentCourseIdentifier);
                })
                // 학년 필터링은 추천 알고리즘 내 우선순위 정렬로 처리 (이전 사용자 요청)
                .collect(Collectors.toList());
    }

    public List<RecommendedTimetableDto> generateRecommendations(Long userId) { // userId는 Long
        logger.info("User ID {} 시간표 추천 생성 시작", userId);
        User currentUser = userService.getUserDetails(userId); // User 타입으로 받음
        UserCourseSelectionEntity userSelections = userService.getUserCourseSelection(userId);
        UserPreferenceEntity userPreferences = userService.getUserPreference(userId);

        TimePreferenceRequest timePreferences = Optional.ofNullable(userPreferences.getTimePreferences()).orElseGet(TimePreferenceRequest::new);
        CreditSettingsRequest creditSettings = Optional.ofNullable(userPreferences.getCreditSettings()).orElseGet(CreditSettingsRequest::new);

        List<String> targetCourseTypes = new ArrayList<>();
        if (creditSettings.getCreditGoalsPerType() != null && !creditSettings.getCreditGoalsPerType().isEmpty()) {
            targetCourseTypes.addAll(creditSettings.getCreditGoalsPerType().keySet());
        }

        logger.debug("User ID {}: 사용자 정보(학년 {}): {}, 수강선택: {}, 시간선호: {}, 학점설정: {}, 목표유형: {}",
                userId, currentUser.getGrade(), currentUser.getStudentId(), userSelections, timePreferences, creditSettings, targetCourseTypes); // currentUser.getStudentId() 등으로 변경 가능

        List<DetailedCourseInfo> allCourses = courseDataService.getDetailedCourses();
        if (allCourses.isEmpty()) {
            logger.warn("User ID {}: 로드된 강의 데이터가 없습니다.", userId);
            return Collections.emptyList();
        }
        logger.debug("User ID {}: 전체 강의 수: {}", userId, allCourses.size());

        List<DetailedCourseInfo> candidatePool = prepareCandidateCourses(allCourses, userSelections, currentUser.getGrade(), currentUser, creditSettings); // grade는 int
        logger.debug("User ID {}: 초기 후보 강의 수 (기수강/동일과목 제외): {}", userId, candidatePool.size());

        List<DetailedCourseInfo> mandatoryScheduledCourses = getAndValidateMandatoryCourses(candidatePool, userSelections, currentUser, creditSettings);
        if (mandatoryScheduledCourses == null) {
            logger.error("User ID {}: 필수/재수강 과목 간 시간 중복으로 추천 생성 불가.", userId);
            throw new IllegalArgumentException("필수 또는 재수강 과목 간에 시간이 중복됩니다. 선택을 조정한 후 다시 시도해주세요.");
        }
        logger.info("User ID {}: 필수/재수강 과목 처리 완료 ({}개)", userId, mandatoryScheduledCourses.size());
        mandatoryScheduledCourses.forEach(c -> logger.debug("  User ID {}: 필수/재수강 포함: {} ({}) - 실제유형: {}", userId, c.getCourseName(), c.getCourseCode(), getActualCourseTypeForUser(c, currentUser, creditSettings)));

        List<List<DetailedCourseInfo>> generatedRawTimetables = findTimetableCombinations(
                mandatoryScheduledCourses, candidatePool,
                timePreferences, creditSettings, 3, currentUser, targetCourseTypes
        );
        logger.info("User ID {}: {}개의 원시 시간표 조합 생성됨.", userId, generatedRawTimetables.size());

        List<RecommendedTimetableDto> recommendations = new ArrayList<>();
        for (int i = 0; i < generatedRawTimetables.size(); i++) {
            List<DetailedCourseInfo> timetableCourses = generatedRawTimetables.get(i);
            Map<String, Integer> creditsByType = calculateCreditsByTypeForUser(timetableCourses, currentUser, creditSettings, targetCourseTypes);
            int totalCredits = creditsByType.values().stream().mapToInt(Integer::intValue).sum();
            recommendations.add(convertToRecommendedDtoForUser(i + 1, timetableCourses, creditsByType, totalCredits, currentUser, creditSettings));
        }

        if (recommendations.isEmpty() && !mandatoryScheduledCourses.isEmpty()) {
            if (meetsAllCreditCriteriaForUser(mandatoryScheduledCourses, currentUser, creditSettings, targetCourseTypes)) {
                Map<String, Integer> mandatoryCreditsByType = calculateCreditsByTypeForUser(mandatoryScheduledCourses, currentUser, creditSettings, targetCourseTypes);
                int mandatoryTotalCredits = mandatoryCreditsByType.values().stream().mapToInt(Integer::intValue).sum();
                recommendations.add(convertToRecommendedDtoForUser(0, mandatoryScheduledCourses, mandatoryCreditsByType, mandatoryTotalCredits, currentUser, creditSettings));
                logger.info("User ID {}: 필수 과목만으로 구성된 시간표를 추천합니다.", userId);
            }
        }

        if (recommendations.isEmpty()) {
            logger.info("User ID {}: 최종 추천 시간표를 생성하지 못했습니다.", userId);
        } else {
            logger.info("User ID {}: 최종 {}개의 시간표 추천.", userId, recommendations.size());
        }
        return recommendations;
    }

    private List<DetailedCourseInfo> filterByTimePreferences(List<DetailedCourseInfo> courses, TimePreferenceRequest preferences) {
        // 1. 사용자가 선호 시간대를 아예 설정하지 않았다면, 모든 과목을 그대로 반환합니다.
        if (preferences == null || preferences.getPreferredTimeSlots() == null || preferences.getPreferredTimeSlots().isEmpty()) {
            return courses;
        }

        // 2. 사용자가 선택한 모든 선호 시간대를 '허용된 시간 지도'로 만듭니다.
        //    예: { "Mon": {1, 2, 3, 4}, "Wed": {3, 4} }
        Map<String, Set<Integer>> allowedSlotsMap = new HashMap<>();
        for (TimeSlotDto preferredSlot : preferences.getPreferredTimeSlots()) {
            if (preferredSlot.getDay() != null && preferredSlot.getPeriods() != null) {
                allowedSlotsMap
                        .computeIfAbsent(preferredSlot.getDay(), k -> new HashSet<>())
                        .addAll(preferredSlot.getPeriods());
            }
        }

        // 3. 전체 과목 목록에서, '허용된 시간 지도' 안에 완전히 포함되는 과목만 남깁니다.
        return courses.stream().filter(course -> {
            // 강의에 시간 정보가 없으면 추천 대상에서 제외합니다.
            if (course.getScheduleSlots() == null || course.getScheduleSlots().isEmpty()) {
                return false;
            }

            // 해당 과목의 '모든' 수업 시간이 '허용된 시간 지도'에 포함되어야 합니다.
            for (TimeSlotDto courseSlot : course.getScheduleSlots()) {
                Set<Integer> allowedPeriods = allowedSlotsMap.get(courseSlot.getDay());
                // 해당 요일에 허용된 시간이 없거나(null),
                // 과목의 교시들 중 허용되지 않은 교시가 하나라도 있다면(!containsAll) 이 과목은 탈락입니다.
                if (allowedPeriods == null || !allowedPeriods.containsAll(courseSlot.getPeriods())) {
                    return false;
                }
            }
            // 모든 시간 검사를 통과한 과목만 살아남습니다.
            return true;
        }).collect(Collectors.toList());
    }


    // TimetableService.java 내부에 위치

    // TimetableService.java 내부에 위치

    private List<List<DetailedCourseInfo>> findTimetableCombinations(
            List<DetailedCourseInfo> initialTimetableBase,
            List<DetailedCourseInfo> availableCoursePool,
            TimePreferenceRequest timePreferences,
            CreditSettingsRequest creditSettings,
            int numRecommendationsNeeded,
            User currentUser,
            List<String> targetCourseTypes) {

        List<List<DetailedCourseInfo>> validTimetables = new ArrayList<>();
        List<DetailedCourseInfo> currentCombination = new ArrayList<>(initialTimetableBase);

        Set<String> initialCourseGroupIds = initialTimetableBase.stream()
                .map(DetailedCourseInfo::getGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 1. 시간 선호도에 맞는 과목만 먼저 걸러냅니다.
        List<DetailedCourseInfo> timeFilteredPool = filterByTimePreferences(availableCoursePool, timePreferences);

        // 2. 그 결과 중에서 추가적인 필터링을 수행합니다.
        List<DetailedCourseInfo> electivePool = timeFilteredPool.stream()
                // 필수/재수강으로 이미 선택된 동일계열 과목 제외
                .filter(c -> c.getGroupId() == null || !initialCourseGroupIds.contains(c.getGroupId()))

                // ★★★ 핵심 성능 개선 필터 ★★★
                .filter(course -> {
                    // 사용자가 학점 목표를 설정한 유형이 없다면 모든 과목을 대상으로 함
                    if (targetCourseTypes.isEmpty()) {
                        return true;
                    }

                    // 과목의 실제 유형을 판단
                    String actualType = getActualCourseTypeForUser(course, currentUser, creditSettings);

                    // 사용자가 목표를 설정한 유형의 과목이거나, "자선" 또는 "일반선택" 과목만 후보로 인정
                    return targetCourseTypes.contains(actualType) || "자선".equals(actualType) || "일반선택".equals(actualType);
                })
                .collect(Collectors.toList());

        logger.debug("User ID {}: 초기 조합 ({}개), 최종 필터링된 선택 가능 풀 ({}개)",
                currentUser.getId(),
                currentCombination.size(),
                electivePool.size()
        );

        // 이제 대폭 줄어든 electivePool을 가지고 조합을 시작합니다.
        generateCombinationsRecursive(
                electivePool, 0, currentCombination, validTimetables, timePreferences,
                creditSettings, numRecommendationsNeeded, currentUser, targetCourseTypes
        );

        return validTimetables.stream().limit(numRecommendationsNeeded).collect(Collectors.toList());
    }

    private void generateCombinationsRecursive(
            List<DetailedCourseInfo> electives, int startIndex,
            List<DetailedCourseInfo> currentCombination,
            List<List<DetailedCourseInfo>> validTimetables,
            TimePreferenceRequest timePreferences,
            CreditSettingsRequest creditSettings,
            int numRecommendationsNeeded,
            User currentUser,
            List<String> targetCourseTypes) {

        if (validTimetables.size() >= numRecommendationsNeeded) {
            return;
        }

        Map<String, Integer> currentCreditsByType = calculateCreditsByTypeForUser(currentCombination, currentUser, creditSettings, targetCourseTypes);
        int currentTotalCredits = currentCreditsByType.values().stream().mapToInt(Integer::intValue).sum();

        // 1. 학점 조건만 만족하는지 확인합니다.
        if (meetsAllCreditCriteriaForUser(currentCombination, currentUser, creditSettings, targetCourseTypes)) {

            // 이미 선호 시간에 맞는 과목들만 넘어왔기 때문에, 이 검사는 더 이상 필요 없습니다.
            Set<String> currentCombinationGroupIds = currentCombination.stream()
                    .map(DetailedCourseInfo::getGroupId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            boolean isDuplicate = validTimetables.stream()
                    .anyMatch(existing -> existing.stream().map(DetailedCourseInfo::getGroupId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet())
                            .equals(currentCombinationGroupIds) && existing.size() == currentCombination.size());

            if (!isDuplicate) {
                validTimetables.add(new ArrayList<>(currentCombination));
                logger.info("User ID {}: 유효한 시간표 발견! (현재 {}개 찾음) 과목: [{}], 총학점: {}",
                        currentUser.getId(), validTimetables.size(),
                        currentCombination.stream().map(DetailedCourseInfo::getCourseName).collect(Collectors.joining(", ")),
                        currentTotalCredits);
                if (validTimetables.size() >= numRecommendationsNeeded) return;
            }
        }

        if (startIndex >= electives.size() ||
                (creditSettings.getMaxTotalCredits() != null && currentTotalCredits >= creditSettings.getMaxTotalCredits())) {
            return;
        }

        for (int i = startIndex; i < electives.size(); i++) {
            DetailedCourseInfo courseToAdd = electives.get(i);

            if (courseToAdd.getGroupId() != null) {
                final String currentGroupId = courseToAdd.getGroupId();
                boolean groupAlreadyInCombination = currentCombination.stream()
                        .anyMatch(existingCourse -> Objects.equals(existingCourse.getGroupId(), currentGroupId));
                if (groupAlreadyInCombination) {
                    continue;
                }
            }

            String actualCourseType = getActualCourseTypeForUser(courseToAdd, currentUser, creditSettings);
            if (!targetCourseTypes.isEmpty()) {
                if (!targetCourseTypes.contains(actualCourseType) &&
                        !"기타".equals(actualCourseType) && !"일반선택".equals(actualCourseType)) {
                    logger.trace("User ID {}: 과목 {} ({})은 사용자가 학점 목표를 설정한 유형({})에 없어 건너뜀", currentUser.getId(), courseToAdd.getCourseName(), actualCourseType, targetCourseTypes);
                    continue;
                }
            }

            if (canAddCourseCreditWiseForUser(courseToAdd, currentCreditsByType, currentTotalCredits, creditSettings, currentUser, targetCourseTypes)) {
                currentCombination.add(courseToAdd);
                if (!hasTimeConflictInList(currentCombination)) {
                    logger.trace("User ID {}: 과목 추가 시도: {}, 현재 조합: [{}], (총 {}학점)", currentUser.getId(), courseToAdd.getCourseName(), currentCombination.stream().map(DetailedCourseInfo::getCourseName).collect(Collectors.joining(", ")), currentTotalCredits + courseToAdd.getCredits());
                    generateCombinationsRecursive(electives, i + 1, currentCombination, validTimetables, timePreferences, creditSettings, numRecommendationsNeeded, currentUser, targetCourseTypes);
                } else {
                    logger.trace("User ID {}: 과목 {} 추가 시 시간 충돌 발생", currentUser.getId(), courseToAdd.getCourseName());
                }
                currentCombination.remove(currentCombination.size() - 1);
            } else {
                logger.trace("User ID {}: 과목 {} 추가 시 학점 조건 위반", currentUser.getId(), courseToAdd.getCourseName());
            }
            if (validTimetables.size() >= numRecommendationsNeeded) return;
        }
    }

    private int compareCourseGradePriority(DetailedCourseInfo c1, DetailedCourseInfo c2, int userNumericGrade) {
        // (이전 답변과 동일)
        int g1Priority = getGradePriorityValue(c1.getGrade(), userNumericGrade);
        int g2Priority = getGradePriorityValue(c2.getGrade(), userNumericGrade);
        return Integer.compare(g1Priority, g2Priority);
    }

    private int getGradePriorityValue(String courseGradeStr, int userNumericGrade) {
        // (이전 답변과 동일)
        if (courseGradeStr == null || courseGradeStr.equalsIgnoreCase("정보없음")) return 1000;
        try {
            String numericOnlyCourseGrade = courseGradeStr.replaceAll("[^0-9]", "");
            if (!numericOnlyCourseGrade.isEmpty()) {
                int cGrade = Integer.parseInt(numericOnlyCourseGrade);
                if (cGrade == userNumericGrade) return 0;
                return Math.abs(cGrade - userNumericGrade);
            }
        } catch (NumberFormatException e) { /* 무시 */ }
        return 1000;
    }

    private boolean hasTimeConflictInList(List<DetailedCourseInfo> courses) {
        // (이전 답변과 동일, 로깅 강화)
        if (courses == null || courses.isEmpty()) return false;
        List<TimeSlotDto> allSlots = courses.stream()
                .filter(c -> c.getScheduleSlots() != null && !c.getScheduleSlots().isEmpty())
                .flatMap(course -> course.getScheduleSlots().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (allSlots.isEmpty()) return false;

        Map<String, Set<Integer>> dailySchedule = new HashMap<>();
        for (TimeSlotDto slot : allSlots) {
            if (slot.getDay() == null || slot.getPeriods() == null || slot.getPeriods().isEmpty()) continue;

            Set<Integer> periodsForDay = dailySchedule.computeIfAbsent(slot.getDay(), k -> new HashSet<>());
            for (Integer period : slot.getPeriods()) {
                if (!periodsForDay.add(period)) {
                    logger.debug("시간 충돌 감지: Day={}, Period={}, 충돌 유발 과목들: {}", slot.getDay(), period,
                            courses.stream()
                                    .filter(c-> c.getScheduleSlots() != null && c.getScheduleSlots().stream()
                                            .anyMatch(s-> Objects.equals(s.getDay(), slot.getDay()) && s.getPeriods() != null && s.getPeriods().contains(period)))
                                    .map(c -> c.getCourseName() + "(" + c.getCourseCode() + ")")
                                    .collect(Collectors.toList()));
                    return true;
                }
            }
        }
        return false;
    }

    private List<DetailedCourseInfo> getAndValidateMandatoryCourses(List<DetailedCourseInfo> candidatePool,
                                                                    UserCourseSelectionEntity selections,
                                                                    User currentUser,
                                                                    CreditSettingsRequest creditSettings) {
        Set<String> mandatoryAndRetakeCodesFromSelection = new HashSet<>();
        if (selections.getMandatoryCourses() != null) mandatoryAndRetakeCodesFromSelection.addAll(selections.getMandatoryCourses());
        if (selections.getRetakeCourses() != null) mandatoryAndRetakeCodesFromSelection.addAll(selections.getRetakeCourses());

        if (mandatoryAndRetakeCodesFromSelection.isEmpty()) return Collections.emptyList();

        List<DetailedCourseInfo> initialMandatoryCourses = candidatePool.stream()
                .filter(course -> mandatoryAndRetakeCodesFromSelection.contains(course.getCourseCode()))
                .distinct()
                .collect(Collectors.toList());

        Map<String, DetailedCourseInfo> mandatoryCoursesByGroupId = new LinkedHashMap<>();
        for (DetailedCourseInfo course : initialMandatoryCourses) {
            String identifier = course.getGroupId() != null ? course.getGroupId() : course.getCourseCode();
            mandatoryCoursesByGroupId.putIfAbsent(identifier, course);
        }
        List<DetailedCourseInfo> validatedMandatoryCourses = new ArrayList<>(mandatoryCoursesByGroupId.values());

        // ★★★ 핵심 수정 부분 ★★★
        if (hasTimeConflictInList(validatedMandatoryCourses)) {
            // 시간이 중복되면, 어떤 과목들이 문제인지 메시지를 만들어 새로운 예외를 발생시킨다.
            String conflictingCourses = validatedMandatoryCourses.stream()
                    .map(c -> c.getCourseName() + "(" + c.getCourseCode() + ")")
                    .collect(Collectors.joining(", "));
            logger.error("User ID {}: 필수/재수강 과목 간 시간 중복 발생: {}", currentUser.getId(), conflictingCourses);
            throw new MandatoryCourseConflictException("필수/재수강 과목 간 시간이 중복됩니다: " + conflictingCourses);
        }
        return validatedMandatoryCourses;
    }
    private boolean canAddCourseCreditWiseForUser(DetailedCourseInfo courseToAdd,
                                                  Map<String, Integer> currentCreditsByType,
                                                  int currentTotalCredits,
                                                  CreditSettingsRequest creditSettings,
                                                  User currentUser, // User 타입으로 변경
                                                  List<String> targetCourseTypes) {

        String actualCourseType = getActualCourseTypeForUser(courseToAdd, currentUser, creditSettings);
        int creditsOfCourseToAdd = courseToAdd.getCredits();

        boolean isRelevantForCreditGoalCheck = targetCourseTypes.isEmpty() || targetCourseTypes.contains(actualCourseType) ||
                "기타".equals(actualCourseType) || "일반선택".equals(actualCourseType);

        int newCreditsForType = currentCreditsByType.getOrDefault(actualCourseType, 0) + creditsOfCourseToAdd;
        int newTotalCredits = currentTotalCredits + creditsOfCourseToAdd;

        Map<String, CreditRangeDto> rangesPerType = creditSettings.getCreditGoalsPerType();
        if (rangesPerType != null && isRelevantForCreditGoalCheck && rangesPerType.containsKey(actualCourseType)) {
            CreditRangeDto typeRange = rangesPerType.get(actualCourseType);
            if (typeRange != null && newCreditsForType > typeRange.getMax()) {
                logger.trace("과목 {} (유형:{}) 추가 시 {} 유형 최대 학점({}) 초과 (현재 {} + 추가 {} = {})", courseToAdd.getCourseName(), actualCourseType, actualCourseType, typeRange.getMax(), currentCreditsByType.getOrDefault(actualCourseType, 0), creditsOfCourseToAdd, newCreditsForType);
                return false;
            }
        }

        if (creditSettings.getMaxTotalCredits() != null && newTotalCredits > creditSettings.getMaxTotalCredits()) {
            logger.trace("과목 {} 추가 시 전체 최대 학점({}) 초과 (현재 {} + 추가 {} = {})", courseToAdd.getCourseName(), creditSettings.getMaxTotalCredits(), currentTotalCredits, creditsOfCourseToAdd, newTotalCredits);
            return false;
        }
        return true;
    }

    private boolean meetsAllCreditCriteriaForUser(List<DetailedCourseInfo> timetable, User currentUser, CreditSettingsRequest creditSettings, List<String> targetCourseTypes) { // User 타입으로 변경
        if (timetable.isEmpty()) {
            return !(creditSettings.getMinTotalCredits() != null && creditSettings.getMinTotalCredits() > 0);
        }

        Map<String, Integer> currentCreditsByType = calculateCreditsByTypeForUser(timetable, currentUser, creditSettings, targetCourseTypes);
        int currentTotalCredits = currentCreditsByType.values().stream().mapToInt(Integer::intValue).sum();

        logger.trace("학점 조건 검사 - User ID {}: 총 학점: {}, 유형별 학점: {}", currentUser.getId(), currentTotalCredits, currentCreditsByType);

        if (creditSettings.getMinTotalCredits() != null && currentTotalCredits < creditSettings.getMinTotalCredits()) {
            logger.trace("  -> 전체 최소 학점({}) 미달", creditSettings.getMinTotalCredits());
            return false;
        }
        if (creditSettings.getMaxTotalCredits() != null && currentTotalCredits > creditSettings.getMaxTotalCredits()) {
            logger.trace("  -> 전체 최대 학점({}) 초과", creditSettings.getMaxTotalCredits());
            return false;
        }

        if (!targetCourseTypes.isEmpty() && creditSettings.getCreditGoalsPerType() != null && !creditSettings.getCreditGoalsPerType().isEmpty()) {
            for (String userDefinedType : targetCourseTypes) {
                CreditRangeDto range = creditSettings.getCreditGoalsPerType().get(userDefinedType);
                int creditsForThisType = currentCreditsByType.getOrDefault(userDefinedType, 0);
                if (range != null) {
                    if (creditsForThisType < range.getMin()) {
                        logger.trace("  -> {} 유형 최소 학점({}) 미달 (현재 {})", userDefinedType, range.getMin(), creditsForThisType);
                        return false;
                    }
                    if (creditsForThisType > range.getMax()) {
                        logger.trace("  -> {} 유형 최대 학점({}) 초과 (현재 {})", userDefinedType, range.getMax(), creditsForThisType);
                        return false;
                    }
                } else {
                    logger.warn("  -> User ID {}: 사용자가 듣기로 한 유형 {} 에 대한 학점 범위 설정(CreditRangeDto)이 없습니다 (현재 {} 학점).", currentUser.getId(), userDefinedType, creditsForThisType);
                }
            }
        }
        logger.trace("  -> User ID {}: 모든 학점 조건 만족", currentUser.getId());
        return true;
    }

    private Map<String, Integer> calculateCreditsByTypeForUser(List<DetailedCourseInfo> courses, User currentUser, CreditSettingsRequest creditSettings, List<String> targetCourseTypes) { // User 타입으로 변경
        Map<String, Integer> creditsMap = new HashMap<>();
        List<String> typesToInitializeInMap = new ArrayList<>();

        if (targetCourseTypes != null && !targetCourseTypes.isEmpty()){
            typesToInitializeInMap.addAll(targetCourseTypes);
        }

        courses.stream()
                .map(c -> getActualCourseTypeForUser(c, currentUser, creditSettings))
                .filter(Objects::nonNull).distinct()
                .forEach(type -> {
                    if(!typesToInitializeInMap.contains(type)) typesToInitializeInMap.add(type);
                });

        typesToInitializeInMap.add("기타");
        typesToInitializeInMap.add("일반선택");

        for(String type : typesToInitializeInMap.stream().distinct().collect(Collectors.toList())) {
            creditsMap.put(type, 0);
        }

        for (DetailedCourseInfo course : courses) {
            String actualCourseType = getActualCourseTypeForUser(course, currentUser, creditSettings);
            if (actualCourseType == null) actualCourseType = "기타";
            creditsMap.put(actualCourseType, creditsMap.getOrDefault(actualCourseType, 0) + course.getCredits());
        }
        return creditsMap;
    }

    private RecommendedTimetableDto convertToRecommendedDtoForUser(int id, List<DetailedCourseInfo> courses, Map<String, Integer> creditsByType, int totalCredits, User currentUser, CreditSettingsRequest creditSettings) { // User 타입으로 변경
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