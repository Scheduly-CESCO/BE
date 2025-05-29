package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.timetable.*;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.UserEntity;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.model.DetailedCourseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimetableService {

    private static final Logger logger = LoggerFactory.getLogger(TimetableService.class);

    private final UserService userService;

    private final CourseDataService courseDataService;

    public TimetableService(Userservice userService, CourseDataService courseDataService) {
        this.userService = userService;
        this.courseDataService = courseDataService;
    }

    // 사용자별로 과목의 실제 유형("전공", "이중전공", "교양", "자선" 등)을 최종 판단
    private String getActualCourseTypeForUser(DetailedCourseInfo course, UserEntity currentUser, CreditSettingsRequest creditSettings) {
        if (course == null || currentUser == null) {
            logger.warn("getActualCourseTypeForUser 호출 시 course 또는 currentUser가 null입니다.");
            return "기타";
        }

        String primaryMajorName = currentUser.getMajor();
        String secondaryMajorName = currentUser.getDoubleMajor();
        String courseSpecificMajor = course.getSpecificMajor(); // "세부전공" 필드값
        String courseDeptOriginal = course.getDepartmentOriginal(); // 원본 "개설영역"
        String courseGeneralizedType = course.getGeneralizedType(); // CourseDataService 1차 분류 타입

        // 1. 이중전공 판단 (사용자 이중전공명과 과목의 "세부전공" 필드 일치 최우선)
        if (secondaryMajorName != null && !secondaryMajorName.isBlank() &&
                courseSpecificMajor != null && !courseSpecificMajor.isBlank() &&
                courseSpecificMajor.equalsIgnoreCase(secondaryMajorName)) {
            return "이중전공";
        }

        // 2. 주전공 판단 (사용자 주전공명과 과목의 "세부전공" 필드 일치 최우선)
        if (primaryMajorName != null && !primaryMajorName.isBlank() &&
                courseSpecificMajor != null && !courseSpecificMajor.isBlank() &&
                courseSpecificMajor.equalsIgnoreCase(primaryMajorName)) {
            return "전공";
        }

        // 3. "자선" 과목 판단 (학생의 새 정의: "개설영역"이 "전공"이고, 위에서 사용자의 주/이중전공("세부전공" 기준)으로 판단되지 않은 경우)
        if (courseDeptOriginal != null && courseDeptOriginal.equalsIgnoreCase("전공")) {
            // 이 과목이 위 1,2번에서 사용자의 주/이중전공으로 "세부전공" 기준으로 매칭되지 않았다면 "자선"
            // (courseSpecificMajor가 null이거나, 사용자의 전공/이중전공과 다른 경우)
            // 이미 위에서 주/이중전공 매칭을 courseSpecificMajor 기준으로 했으므로, 여기까지 왔다면 자선 후보.
            return "자선";
        }

        // 4. 그 외: CourseDataService의 1차 분류된 generalizedType 사용
        // "전공_후보"는 위에서 "세부전공"과 매칭되지 않았고, "개설영역"도 "전공"이 아니라면 (예: 순수 학과명) "기타" 또는 "일반선택" 가능
        if (Objects.equals(courseGeneralizedType, "전공_후보")) {
            // 사용자의 주/이중전공과 departmentOriginal(예: "컴퓨터공학부")을 비교하여 판단 가능
            if (primaryMajorName != null && !primaryMajorName.isBlank() &&
                    courseDeptOriginal != null && courseDeptOriginal.toLowerCase().contains(primaryMajorName.toLowerCase())) {
                return "전공";
            }
            if (secondaryMajorName != null && !secondaryMajorName.isBlank() &&
                    courseDeptOriginal != null && courseDeptOriginal.toLowerCase().contains(secondaryMajorName.toLowerCase())) {
                return "이중전공";
            }
            return "일반선택"; // 또는 "타전공"
        }

        // "교양", "군사학", "교직", "신입생세미나" 등 명확한 타입 반환
        if (courseGeneralizedType != null &&
                !courseGeneralizedType.equals("미분류_전공성") && // 이 타입은 CourseDataService에서 더 이상 사용 안 할 수 있음
                !courseGeneralizedType.equals("기타")) {
            return courseGeneralizedType;
        }

        return "기타"; // 위 모든 경우에 해당하지 않으면 "기타"
    }

    // 동일 과목 판단 (groupId 사용)
    private boolean areCoursesEffectivelySame(DetailedCourseInfo course1, DetailedCourseInfo course2) {
        if (course1 == null || course2 == null) return false;
        // 학수번호가 완벽히 같으면 당연히 동일 과목
        if (Objects.equals(course1.getCourseCode(), course2.getCourseCode())) return true;
        // groupId (학수번호 앞 7자리)가 존재하고 서로 같으면 동일 과목으로 간주
        return course1.getGroupId() != null && course1.getGroupId().equals(course2.getGroupId());
    }

    private List<DetailedCourseInfo> prepareCandidateCourses(List<DetailedCourseInfo> allCourses,
                                                             UserCourseSelectionEntity selections,
                                                             String userGrade, // 사용자 학년 (예: "1", "2")
                                                             UserEntity currentUser,
                                                             CreditSettingsRequest creditSettings) {
        Set<String> takenCourseGroupIds = new HashSet<>();
        Set<String> retakeCourseGroupIds = new HashSet<>();

        if (selections.getRetakeCourses() != null) {
            for (String retakeCode : selections.getRetakeCourses()) {
                DetailedCourseInfo retakeCourse = courseDataService.getDetailedCourseByCode(retakeCode);
                if (retakeCourse != null && retakeCourse.getGroupId() != null) {
                    retakeCourseGroupIds.add(retakeCourse.getGroupId());
                } else if (retakeCourse != null) { // groupId가 없는 경우 대비 (학수번호 자체로)
                    retakeCourseGroupIds.add(retakeCourse.getCourseCode()); // groupId 대신 학수번호 사용
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
                } else if (takenCourse != null) { // groupId 없는 경우
                    if (!retakeCourseGroupIds.contains(takenCourse.getCourseCode())) {
                        takenCourseGroupIds.add(takenCourse.getCourseCode());
                    }
                }
            }
        }

        return allCourses.stream()
                // groupId를 기준으로 기수강/재수강 필터링
                .filter(course -> {
                    String currentCourseIdentifier = course.getGroupId() != null ? course.getGroupId() : course.getCourseCode();
                    return !takenCourseGroupIds.contains(currentCourseIdentifier) || retakeCourseGroupIds.contains(currentCourseIdentifier);
                })
                // 학년 필터링은 여기서 제거 (사용자 요청: 모든 학년 포함 후 추천 알고리즘에서 우선순위 부여)
                .collect(Collectors.toList());
    }

    private List<DetailedCourseInfo> getAndValidateMandatoryCourses(List<DetailedCourseInfo> candidatePool,
                                                                    UserCourseSelectionEntity selections,
                                                                    UserEntity currentUser,
                                                                    CreditSettingsRequest creditSettings) {
        Set<String> mandatoryAndRetakeCodesFromSelection = new HashSet<>();
        if (selections.getMandatoryCourses() != null) mandatoryAndRetakeCodesFromSelection.addAll(selections.getMandatoryCourses());
        if (selections.getRetakeCourses() != null) mandatoryAndRetakeCodesFromSelection.addAll(selections.getRetakeCourses());

        if (mandatoryAndRetakeCodesFromSelection.isEmpty()) return Collections.emptyList();

        List<DetailedCourseInfo> initialMandatoryCourses = candidatePool.stream()
                .filter(course -> mandatoryAndRetakeCodesFromSelection.contains(course.getCourseCode()))
                .distinct()
                .collect(Collectors.toList());

        // groupId 기준으로 중복 제거 (동일 과목 중복 선택 방지)
        Map<String, DetailedCourseInfo> mandatoryCoursesByGroupId = new LinkedHashMap<>();
        for (DetailedCourseInfo course : initialMandatoryCourses) {
            String identifier = course.getGroupId() != null ? course.getGroupId() : course.getCourseCode();
            mandatoryCoursesByGroupId.putIfAbsent(identifier, course);
        }
        List<DetailedCourseInfo> validatedMandatoryCourses = new ArrayList<>(mandatoryCoursesByGroupId.values());

        if (hasTimeConflictInList(validatedMandatoryCourses)) {
            logger.error("User ID {}: 필수/재수강 과목 간 시간 중복 발생: {}", currentUser.getUserId(), validatedMandatoryCourses.stream().map(c -> c.getCourseName() + "(" + c.getCourseCode() + ")").collect(Collectors.joining(", ")));
            return null;
        }
        return validatedMandatoryCourses;
    }

    public List<RecommendedTimetableDto> generateRecommendations(String userId) {
        logger.info("User ID {} 시간표 추천 생성 시작", userId);
        UserEntity currentUser = userService.getUserDetails(userId);
        UserCourseSelectionEntity userSelections = userService.getUserCourseSelection(userId);
        UserPreferenceEntity userPreferences = userService.getUserPreference(userId);

        TimePreferenceRequest timePreferences = Optional.ofNullable(userPreferences.getTimePreferences()).orElseGet(TimePreferenceRequest::new);
        CreditSettingsRequest creditSettings = Optional.ofNullable(userPreferences.getCreditSettings()).orElseGet(CreditSettingsRequest::new);

        List<String> targetCourseTypes = new ArrayList<>();
        if (creditSettings.getCreditGoalsPerType() != null && !creditSettings.getCreditGoalsPerType().isEmpty()) {
            targetCourseTypes.addAll(creditSettings.getCreditGoalsPerType().keySet());
        }
        if (targetCourseTypes.isEmpty()) {
            logger.warn("User ID {}: 사용자가 학점 목표를 설정한 과목 유형이 없습니다. 추천 로직이 제한될 수 있습니다.", userId);
            // 기본적으로 모든 유형을 고려하거나, "전공", "교양" 등을 기본 목표로 설정할 수 있습니다.
            // 여기서는 비어있는 경우, 알고리즘 내에서 과목 유형 필터링을 덜 엄격하게 할 수 있습니다.
        }

        logger.debug("User ID {}: 사용자 정보(학년 {}): {}, 수강선택: {}, 시간선호: {}, 학점설정: {}, 목표유형: {}",
                userId, currentUser.getGrade(), currentUser, userSelections, timePreferences, creditSettings, targetCourseTypes);

        List<DetailedCourseInfo> allCourses = courseDataService.getDetailedCourses();
        if (allCourses.isEmpty()) {
            logger.warn("User ID {}: 로드된 강의 데이터가 없습니다.", userId);
            return Collections.emptyList();
        }
        logger.debug("User ID {}: 전체 강의 수: {}", userId, allCourses.size());

        List<DetailedCourseInfo> candidatePool = prepareCandidateCourses(allCourses, userSelections, currentUser.getGrade(), currentUser, creditSettings);
        logger.debug("User ID {}: 초기 후보 강의 수 (기수강/동일과목 제외): {}", userId, candidatePool.size());

        List<DetailedCourseInfo> mandatoryScheduledCourses = getAndValidateMandatoryCourses(candidatePool, userSelections, currentUser, creditSettings);
        if (mandatoryScheduledCourses == null) {
            logger.error("User ID {}: 필수/재수강 과목 간 시간 중복으로 추천 생성 불가.", userId);
            throw new IllegalArgumentException("필수 또는 재수강 과목 간에 시간이 중복되거나, 동일 과목이 중복 선택되었습니다. 선택을 조정한 후 다시 시도해주세요.");
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

    // 시간 선호도에 따라 강의 목록 필터링 및 정렬 (학년 우선순위 적용)
    private List<DetailedCourseInfo> filterAndSortByTimePreferences(List<DetailedCourseInfo> courses, TimePreferenceRequest preferences, UserEntity currentUser) {
        if (preferences == null && currentUser == null) return new ArrayList<>(courses); // 둘 다 없으면 원본 복사

        List<DetailedCourseInfo> filteredCourses = courses.stream()
                .filter(course -> meetsIndividualCourseTimePreferences(course, preferences)) // 기피 조건 필터링
                .collect(Collectors.toList());

        // 학년 우선순위 정렬
        final String userGradeStr = currentUser != null ? currentUser.getGrade() : null;
        if (userGradeStr != null && !userGradeStr.equalsIgnoreCase("정보없음")) {
            try {
                int userNumericGrade = Integer.parseInt(userGradeStr.replaceAll("[^0-9]", ""));
                filteredCourses.sort((c1, c2) -> compareCourseGradePriority(c1, c2, userNumericGrade));
            } catch (NumberFormatException e) {
                logger.warn("정렬을 위한 사용자 학년 파싱 실패: {}", userGradeStr);
            }
        }
        // TODO: 그 외 선호도(예: 오전/오후, 특정 요일 집중 등)에 따른 추가 정렬 로직

        logger.debug("User ID {}: 시간 선호도 필터링 및 학년 우선순위 정렬 후 선택 가능 과목 수: {}", (currentUser != null ? currentUser.getUserId() : "N/A"), filteredCourses.size());
        return filteredCourses;
    }

    // 개별 과목이 사용자의 시간 선호도(특히 기피 조건)를 만족하는지 확인
    private boolean meetsIndividualCourseTimePreferences(DetailedCourseInfo course, TimePreferenceRequest preferences) {
        // (이전 답변의 로직과 동일 또는 유사하게 유지)
        if (preferences == null) return true;
        if (course.getScheduleSlots() == null || course.getScheduleSlots().isEmpty()) return true;

        for (TimeSlotDto slot : course.getScheduleSlots()) {
            if (slot.getDay() == null || slot.getPeriods() == null || slot.getPeriods().isEmpty()) continue;

            if (preferences.getAvoidDays() != null && !preferences.getAvoidDays().isEmpty() &&
                    preferences.getAvoidDays().contains(slot.getDay())) {
                logger.trace("기피 요일 위반으로 과목 제외: {} ({})", course.getCourseName(), slot.getDay());
                return false;
            }
            if (preferences.getAvoidTimeSlots() != null && !preferences.getAvoidTimeSlots().isEmpty()) {
                for (TimeSlotDto avoidSlot : preferences.getAvoidTimeSlots()) {
                    if (avoidSlot.getDay() != null && avoidSlot.getPeriods() != null && !avoidSlot.getPeriods().isEmpty() &&
                            avoidSlot.getDay().equals(slot.getDay()) &&
                            !Collections.disjoint(avoidSlot.getPeriods(), slot.getPeriods())) {
                        logger.trace("기피 시간대 위반으로 과목 제외: {} ({} {}교시)", course.getCourseName(), slot.getDay(), slot.getPeriods());
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private List<List<DetailedCourseInfo>> findTimetableCombinations(
            List<DetailedCourseInfo> initialTimetableBase,
            List<DetailedCourseInfo> availableCoursePool,
            TimePreferenceRequest timePreferences,
            CreditSettingsRequest creditSettings,
            int numRecommendationsNeeded,
            UserEntity currentUser,
            List<String> targetCourseTypes) { // 사용자가 학점 목표를 설정한 유형들

        List<List<DetailedCourseInfo>> validTimetables = new ArrayList<>();
        List<DetailedCourseInfo> currentCombination = new ArrayList<>(initialTimetableBase);

        Set<String> initialCourseGroupIds = initialTimetableBase.stream()
                .map(DetailedCourseInfo::getGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<DetailedCourseInfo> electivePool = availableCoursePool.stream()
                // 동일 그룹(동일 과목)에 속하는 과목은 이미 초기 조합에 있다면 제외
                .filter(c -> c.getGroupId() == null || !initialCourseGroupIds.contains(c.getGroupId()))
                // 추천 제한 과목 처리
                .filter(c -> {
                    if (c.isRestrictedCourse()) {
                        String actualType = getActualCourseTypeForUser(c, currentUser, creditSettings);
                        // 사용자가 해당 제한된 유형을 듣기로 명시적으로 설정했고 (targetCourseTypes에 포함)
                        // 이 과목의 실제 타입이 그 설정된 타입과 일치하면 선택 가능 풀에 포함
                        if (targetCourseTypes.contains(actualType)) {
                            return true;
                        }
                        return false; // 그 외, 사용자가 명시적으로 선택하지 않은 제한 과목은 제외
                    }
                    return true; // 제한 과목이 아니면 일단 포함
                })
                .collect(Collectors.toList());

        // 시간 선호도 및 학년 우선순위로 정렬된 선택 가능 풀
        electivePool = filterAndSortByTimePreferences(electivePool, timePreferences, currentUser);

        logger.debug("User ID {}: 초기 조합 ({}개): [{}], 정렬된 선택 가능 풀 ({}개): [{}]",
                currentUser.getUserId(),
                currentCombination.size(),
                currentCombination.stream().map(DetailedCourseInfo::getCourseName).collect(Collectors.joining(", ")),
                electivePool.size(),
                electivePool.stream().map(DetailedCourseInfo::getCourseName).collect(Collectors.joining(", "))
        );

        generateCombinationsRecursive(
                electivePool, 0, currentCombination, validTimetables,
                timePreferences, creditSettings, numRecommendationsNeeded, currentUser, targetCourseTypes
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
            UserEntity currentUser,
            List<String> targetCourseTypes) {

        if (validTimetables.size() >= numRecommendationsNeeded) {
            return;
        }

        Map<String, Integer> currentCreditsByType = calculateCreditsByTypeForUser(currentCombination, currentUser, creditSettings, targetCourseTypes);
        int currentTotalCredits = currentCreditsByType.values().stream().mapToInt(Integer::intValue).sum();

        // 현재 조합이 모든 학점 조건을 만족하면 유효한 시간표로 추가
        if (meetsAllCreditCriteriaForUser(currentCombination, currentUser, creditSettings, targetCourseTypes)) {
            if (meetsTimePreferences(currentCombination, timePreferences)) {
                Set<String> currentCombinationGroupIds = currentCombination.stream()
                        .map(DetailedCourseInfo::getGroupId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                // 동일 과목 그룹 ID Set으로 중복 조합 확인 (과목 순서 무관)
                boolean isDuplicate = validTimetables.stream()
                        .anyMatch(existing -> {
                            Set<String> existingGroupIds = existing.stream().map(DetailedCourseInfo::getGroupId).filter(Objects::nonNull).collect(Collectors.toSet());
                            return existingGroupIds.equals(currentCombinationGroupIds) && existing.size() == currentCombination.size();
                        });

                if (!isDuplicate) {
                    validTimetables.add(new ArrayList<>(currentCombination));
                    logger.info("User ID {}: 유효한 시간표 발견! (현재 {}개 찾음) 과목: [{}], 총학점: {}",
                            currentUser.getUserId(), validTimetables.size(),
                            currentCombination.stream().map(DetailedCourseInfo::getCourseName).collect(Collectors.joining(", ")),
                            currentTotalCredits);
                    if (validTimetables.size() >= numRecommendationsNeeded) return;
                }
            }
        }

        // 가지치기 조건: 최대 학점 초과 또는 더 이상 탐색할 과목 없음
        if (startIndex >= electives.size() ||
                (creditSettings.getMaxTotalCredits() != null && currentTotalCredits >= creditSettings.getMaxTotalCredits())) {
            return;
        }

        for (int i = startIndex; i < electives.size(); i++) {
            DetailedCourseInfo courseToAdd = electives.get(i);

            // 동일 그룹 과목 중복 추가 방지 (현재 조합 내에서)
            if (courseToAdd.getGroupId() != null) {
                final String currentGroupId = courseToAdd.getGroupId();
                boolean groupAlreadyInCombination = currentCombination.stream()
                        .anyMatch(existingCourse -> Objects.equals(existingCourse.getGroupId(), currentGroupId));
                if (groupAlreadyInCombination) {
                    continue;
                }
            }

            String actualCourseType = getActualCourseTypeForUser(courseToAdd, currentUser, creditSettings);
            // 사용자가 학점 목표를 설정한 유형의 과목만 고려 (targetCourseTypes가 비어있지 않다면)
            if (!targetCourseTypes.isEmpty()) {
                if (!targetCourseTypes.contains(actualCourseType) &&
                        !"기타".equals(actualCourseType) && !"일반선택".equals(actualCourseType)) {
                    logger.trace("User ID {}: 과목 {} (실제타입:{})은 사용자가 학점 목표를 설정한 유형({})에 없어 건너뜀", currentUser.getUserId(), courseToAdd.getCourseName(), actualCourseType, targetCourseTypes);
                    continue;
                }
            }

            if (canAddCourseCreditWiseForUser(courseToAdd, currentCreditsByType, currentTotalCredits, creditSettings, currentUser, targetCourseTypes)) {
                currentCombination.add(courseToAdd);
                if (!hasTimeConflictInList(currentCombination)) {
                    logger.trace("User ID {}: 과목 추가 시도: {}, 현재 조합: [{}], (총 {}학점)", currentUser.getUserId(), courseToAdd.getCourseName(), currentCombination.stream().map(DetailedCourseInfo::getCourseName).collect(Collectors.joining(", ")), currentTotalCredits + courseToAdd.getCredits());
                    generateCombinationsRecursive(electives, i + 1, currentCombination, validTimetables, timePreferences, creditSettings, numRecommendationsNeeded, currentUser, targetCourseTypes);
                } else {
                    logger.trace("User ID {}: 과목 {} 추가 시 시간 충돌 발생", currentUser.getUserId(), courseToAdd.getCourseName());
                }
                currentCombination.remove(currentCombination.size() - 1); // 백트래킹
            } else {
                logger.trace("User ID {}: 과목 {} 추가 시 학점 조건 위반", currentUser.getUserId(), courseToAdd.getCourseName());
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

    private boolean meetsTimePreferences(List<DetailedCourseInfo> timetable, TimePreferenceRequest preferences) {
        // (이전 답변과 동일, null 체크 강화)
        if (preferences == null) return true;
        boolean noAvoidance = (preferences.getAvoidDays() == null || preferences.getAvoidDays().isEmpty()) &&
                (preferences.getAvoidTimeSlots() == null || preferences.getAvoidTimeSlots().isEmpty());
        boolean noPreferenceDetails = (preferences.getPreferredDays() == null || preferences.getPreferredDays().isEmpty()) &&
                (preferences.getPreferredTimeSlots() == null || preferences.getPreferredTimeSlots().isEmpty()) &&
                (preferences.getPreferredPeriodBlocks() == null || preferences.getPreferredPeriodBlocks().isEmpty());
        boolean noOtherPrefs = preferences.getPreferNoClassDays() == null;

        if (noAvoidance && noPreferenceDetails && noOtherPrefs) return true;

        for (DetailedCourseInfo course : timetable) {
            if (course.getScheduleSlots() == null || course.getScheduleSlots().isEmpty()) continue;
            for (TimeSlotDto slot : course.getScheduleSlots()) {
                if (slot.getDay() == null || slot.getPeriods() == null || slot.getPeriods().isEmpty()) continue;

                if (preferences.getAvoidDays() != null && !preferences.getAvoidDays().isEmpty() && preferences.getAvoidDays().contains(slot.getDay())) {
                    logger.trace("시간표 구성 중 기피 요일 위반: {} ({})", course.getCourseName(), slot.getDay());
                    return false;
                }
                if (preferences.getAvoidTimeSlots() != null && !preferences.getAvoidTimeSlots().isEmpty()) {
                    for (TimeSlotDto avoidSlot : preferences.getAvoidTimeSlots()) {
                        if (avoidSlot.getDay() != null && avoidSlot.getPeriods() != null && !avoidSlot.getPeriods().isEmpty() &&
                                avoidSlot.getDay().equals(slot.getDay()) &&
                                !Collections.disjoint(avoidSlot.getPeriods(), slot.getPeriods())) {
                            logger.trace("시간표 구성 중 기피 시간대 위반: {} ({} {}교시)", course.getCourseName(), slot.getDay(), slot.getPeriods());
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private boolean canAddCourseCreditWiseForUser(DetailedCourseInfo courseToAdd,
                                                  Map<String, Integer> currentCreditsByType,
                                                  int currentTotalCredits,
                                                  CreditSettingsRequest creditSettings,
                                                  UserEntity currentUser,
                                                  List<String> targetCourseTypes) {

        String actualCourseType = getActualCourseTypeForUser(courseToAdd, currentUser, creditSettings);
        int creditsOfCourseToAdd = courseToAdd.getCredits();

        // 이 과목의 실제 유형이 사용자가 듣기로 한 목표 유형 중 하나이거나,
        // 또는 사용자가 아무 유형도 목표로 설정하지 않았을 때만 학점 추가를 고려합니다.
        // "기타", "일반선택"은 targetCourseTypes에 없더라도 학점 계산에는 포함될 수 있습니다.
        boolean isRelevantForCreditGoal = targetCourseTypes.isEmpty() || targetCourseTypes.contains(actualCourseType) ||
                "기타".equals(actualCourseType) || "일반선택".equals(actualCourseType);


        int newCreditsForType = currentCreditsByType.getOrDefault(actualCourseType, 0) + creditsOfCourseToAdd;
        int newTotalCredits = currentTotalCredits + creditsOfCourseToAdd;

        Map<String, CreditRangeDto> rangesPerType = creditSettings.getCreditGoalsPerType();
        // 사용자가 학점 목표를 설정한 유형(targetCourseTypes에 있고, rangesPerType에도 있는)에 대해서만 최대 학점 검사
        if (rangesPerType != null && targetCourseTypes.contains(actualCourseType)) {
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

    private boolean meetsAllCreditCriteriaForUser(List<DetailedCourseInfo> timetable, UserEntity currentUser, CreditSettingsRequest creditSettings, List<String> targetCourseTypes) {
        if (timetable.isEmpty()) {
            // 최소 총 학점 조건이 0이 아니거나, 설정되어 있지 않으면 빈 시간표는 조건 만족 안 함
            return !(creditSettings.getMinTotalCredits() != null && creditSettings.getMinTotalCredits() > 0);
        }

        Map<String, Integer> currentCreditsByType = calculateCreditsByTypeForUser(timetable, currentUser, creditSettings, targetCourseTypes);
        int currentTotalCredits = currentCreditsByType.values().stream().mapToInt(Integer::intValue).sum();

        logger.trace("학점 조건 검사 - User ID {}: 총 학점: {}, 유형별 학점: {}", currentUser.getUserId(), currentTotalCredits, currentCreditsByType);

        if (creditSettings.getMinTotalCredits() != null && currentTotalCredits < creditSettings.getMinTotalCredits()) {
            logger.trace("  -> 전체 최소 학점({}) 미달", creditSettings.getMinTotalCredits());
            return false;
        }
        if (creditSettings.getMaxTotalCredits() != null && currentTotalCredits > creditSettings.getMaxTotalCredits()) {
            logger.trace("  -> 전체 최대 학점({}) 초과", creditSettings.getMaxTotalCredits());
            return false;
        }

        // 사용자가 학점 목표를 설정한 유형(targetCourseTypes)들에 대해서만 최소/최대 학점 검사
        if (!targetCourseTypes.isEmpty() && creditSettings.getCreditGoalsPerType() != null && !creditSettings.getCreditGoalsPerType().isEmpty()) {
            for (String userDefinedType : targetCourseTypes) {
                CreditRangeDto range = creditSettings.getCreditGoalsPerType().get(userDefinedType);
                int creditsForThisType = currentCreditsByType.getOrDefault(userDefinedType, 0);
                // 해당 유형에 대한 학점 범위 설정이 있는 경우에만 검사 (사용자가 해당 유형의 학점을 입력했을 때)
                if (range != null) {
                    if (creditsForThisType < range.getMin()) {
                        logger.trace("  -> {} 유형 최소 학점({}) 미달 (현재 {})", userDefinedType, range.getMin(), creditsForThisType);
                        return false;
                    }
                    // 최대 학점은 canAddCourseCreditWiseForUser에서 이미 검사했으므로, 여기서 다시 검사할 필요는 적음.
                    // 하지만 최종 상태에서 한번 더 확인하는 것은 방어적일 수 있음.
                    if (creditsForThisType > range.getMax()) {
                        logger.trace("  -> {} 유형 최대 학점({}) 초과 (현재 {})", userDefinedType, range.getMax(), creditsForThisType);
                        return false;
                    }
                } else {
                    // 사용자가 듣기로 한 유형(targetCourseTypes에 포함)인데, creditGoalsPerType에 범위 설정이 없는 경우
                    // 이는 CreditSettingsRequest DTO 구성 시 문제가 있었거나, 로직상 허용되지 않는 상태일 수 있음.
                    // 또는, 해당 유형은 학점 제한 없이 듣는다는 의미일 수도 있음. (현재는 제한 없음으로 간주)
                    logger.warn("  -> User ID {}: 사용자가 듣기로 한 유형 {} 에 대한 학점 범위 설정(CreditRangeDto)이 없습니다 (현재 {} 학점). 해당 유형은 학점 제한 없이 간주됩니다.", currentUser.getUserId(), userDefinedType, creditsForThisType);
                }
            }
        }
        logger.trace("  -> User ID {}: 모든 학점 조건 만족", currentUser.getUserId());
        return true;
    }

    private Map<String, Integer> calculateCreditsByTypeForUser(List<DetailedCourseInfo> courses, UserEntity currentUser, CreditSettingsRequest creditSettings, List<String> targetCourseTypes) {
        Map<String, Integer> creditsMap = new HashMap<>();
        List<String> typesToInitializeInMap = new ArrayList<>();

        // 사용자가 학점 목표를 설정한 유형들을 우선적으로 맵에 키로 추가
        if (targetCourseTypes != null && !targetCourseTypes.isEmpty()){
            typesToInitializeInMap.addAll(targetCourseTypes);
        }

        // 시간표에 실제로 포함된 과목들의 유형도 추가 (targetCourseTypes에 없을 수 있는 "기타", "일반선택" 등을 위해)
        courses.stream()
                .map(c -> getActualCourseTypeForUser(c, currentUser, creditSettings))
                .filter(Objects::nonNull).distinct()
                .forEach(type -> {
                    if(!typesToInitializeInMap.contains(type)) typesToInitializeInMap.add(type);
                });

        // 계산 및 결과 표시에 필요한 기본 유형들도 항상 포함
        typesToInitializeInMap.add("기타");
        typesToInitializeInMap.add("일반선택"); // "자선"과 유사한 개념으로 사용될 수 있음

        // 맵 초기화
        for(String type : typesToInitializeInMap.stream().distinct().collect(Collectors.toList())) {
            creditsMap.put(type, 0);
        }

        // 실제 학점 계산
        for (DetailedCourseInfo course : courses) {
            String actualCourseType = getActualCourseTypeForUser(course, currentUser, creditSettings);
            if (actualCourseType == null) actualCourseType = "기타"; // 방어 코드
            creditsMap.put(actualCourseType, creditsMap.getOrDefault(actualCourseType, 0) + course.getCredits());
        }
        return creditsMap;
    }

    private RecommendedTimetableDto convertToRecommendedDtoForUser(int id, List<DetailedCourseInfo> courses, Map<String, Integer> creditsByType, int totalCredits, UserEntity currentUser, CreditSettingsRequest creditSettings) {
        List<ScheduledCourseDto> scheduledCourses = courses.stream()
                .map(course -> new ScheduledCourseDto(
                        course.getCourseCode(),
                        course.getCourseName(),
                        getActualCourseTypeForUser(course, currentUser, creditSettings), // DTO에도 사용자별 실제 유형 반영
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