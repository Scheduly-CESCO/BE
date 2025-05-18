package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.timetable.*;
import com.cesco.scheduly.entity.UserCourseSelectionEntity;
import com.cesco.scheduly.entity.UserPreferenceEntity;
import com.cesco.scheduly.model.DetailedCourseInfo;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimetableService {

    private final UserService userService;
    private final CourseDataService courseDataService;

    public TimetableService(UserService userService, CourseDataService courseDataService) {
        this.userService = userService;
        this.courseDataService = courseDataService;
    }

    public List<RecommendedTimetableDto> generateRecommendations(String userId) {
        // 1. 사용자별 설정 및 정보 로드
        UserCourseSelectionEntity userSelections = userService.getUserCourseSelection(userId);
        UserPreferenceEntity userPreferences = userService.getUserPreference(userId);

        TimePreferenceRequest timePreferences = userPreferences.getTimePreferences();
        if (timePreferences == null) { // 방어 코드: null일 경우 기본 객체 생성
            timePreferences = new TimePreferenceRequest();
        }

        CreditSettingsRequest creditSettings = userPreferences.getCreditSettings();
        if (creditSettings == null) { // 방어 코드: null일 경우 기본 객체 생성
            creditSettings = new CreditSettingsRequest();
        }

        // 2. 전체 강의 목록 로드
        List<DetailedCourseInfo> allCourses = courseDataService.getDetailedCourses();
        if (allCourses.isEmpty()) {
            // 강의 데이터가 없으면 빈 추천 목록 반환 또는 예외 처리
            System.out.println("경고: 로드된 강의 데이터가 없습니다. (TimetableService)");
            return Collections.emptyList();
        }

        // 3. 추천 대상 강의 풀 준비
        String userGrade = userService.getUserDetails(userId).getGrade();
        List<DetailedCourseInfo> candidatePool = prepareCandidateCourses(allCourses, userSelections, userGrade);

        // 4. 필수/재수강 과목 우선 처리 및 충돌 검사
        List<DetailedCourseInfo> mandatoryScheduledCourses = getAndValidateMandatoryCourses(candidatePool, userSelections);
        if (mandatoryScheduledCourses == null) {
            throw new IllegalArgumentException("필수 또는 재수강 과목 간에 시간이 중복됩니다. 선택을 조정한 후 다시 시도해주세요.");
        }

        // 5. 추천 로직 수행
        List<List<DetailedCourseInfo>> generatedRawTimetables = findTimetableCombinations(
                mandatoryScheduledCourses,
                candidatePool,
                userSelections, // 현재 사용되지 않지만, 추후 알고리즘에 활용 가능
                timePreferences,
                creditSettings,
                3 // 목표 추천 개수
        );

        // 6. DTO로 변환하여 반환
        List<RecommendedTimetableDto> recommendations = new ArrayList<>();
        for (int i = 0; i < generatedRawTimetables.size(); i++) {
            List<DetailedCourseInfo> timetableCourses = generatedRawTimetables.get(i);
            Map<String, Integer> creditsByType = calculateCreditsByType(timetableCourses, creditSettings.getCourseTypeCombination());
            int totalCredits = creditsByType.values().stream().mapToInt(Integer::intValue).sum();
            recommendations.add(convertToRecommendedDto(i + 1, timetableCourses, creditsByType, totalCredits));
        }

        // 필수 과목만으로 구성된 경우, 그리고 위에서 시간표를 못 찾은 경우
        if (recommendations.isEmpty() && !mandatoryScheduledCourses.isEmpty()) {
            Map<String, Integer> mandatoryCreditsByType = calculateCreditsByType(mandatoryScheduledCourses, creditSettings.getCourseTypeCombination());
            int mandatoryTotalCredits = mandatoryCreditsByType.values().stream().mapToInt(Integer::intValue).sum();
            if (meetsAllCreditCriteria(mandatoryScheduledCourses, creditSettings)) { // meetsAllCreditCriteria 사용
                recommendations.add(convertToRecommendedDto(0, mandatoryScheduledCourses, mandatoryCreditsByType, mandatoryTotalCredits));
                System.out.println("알림: 필수 과목만으로 구성된 시간표를 추천합니다.");
            }
        }

        if (recommendations.isEmpty()) {
            System.out.println("알림: 최종 추천 시간표를 생성하지 못했습니다. (User ID: " + userId + ")");
        }

        return recommendations;
    }

    private List<DetailedCourseInfo> prepareCandidateCourses(List<DetailedCourseInfo> allCourses, UserCourseSelectionEntity selections, String userGrade) {
        Set<String> takenCodes = (selections.getTakenCourses() != null) ? new HashSet<>(selections.getTakenCourses()) : Collections.emptySet();
        Set<String> retakeCodes = (selections.getRetakeCourses() != null) ? new HashSet<>(selections.getRetakeCourses()) : Collections.emptySet();

        return allCourses.stream()
                .filter(course -> !takenCodes.contains(course.getCourseCode()) || retakeCodes.contains(course.getCourseCode()))
                .filter(course -> {
                    String courseGrade = course.getGrade();
                    if (userGrade == null || userGrade.equalsIgnoreCase("정보없음") ||
                            courseGrade == null || courseGrade.equalsIgnoreCase("정보없음") ||
                            courseGrade.toLowerCase().contains("전학년")) {
                        return true;
                    }
                    try {
                        int targetUserGrade = Integer.parseInt(userGrade.replaceAll("[^0-9]", ""));
                        // "1-2학년"과 같은 경우도 고려 (여기서는 단순 포함으로 처리)
                        if (courseGrade.matches("^[0-9]+$")) { // "1", "2" 등 순수 숫자 학년
                            int targetCourseGrade = Integer.parseInt(courseGrade);
                            return targetCourseGrade <= targetUserGrade;
                        } else if (courseGrade.contains(userGrade.substring(0,1))) { // "1학년" 과 "1" 비교 등
                            return true;
                        }
                        return false; // 복잡한 학년 문자열은 일단 제외 (또는 정교한 파싱 필요)
                    } catch (NumberFormatException e) {
                        return true;
                    }
                })
                .collect(Collectors.toList());
    }

    private List<DetailedCourseInfo> getAndValidateMandatoryCourses(List<DetailedCourseInfo> candidatePool, UserCourseSelectionEntity selections) {
        Set<String> mandatoryAndRetakeCodes = new HashSet<>();
        if (selections.getMandatoryCourses() != null) {
            mandatoryAndRetakeCodes.addAll(selections.getMandatoryCourses());
        }
        if (selections.getRetakeCourses() != null) {
            mandatoryAndRetakeCodes.addAll(selections.getRetakeCourses());
        }

        if (mandatoryAndRetakeCodes.isEmpty()) {
            return Collections.emptyList();
        }

        List<DetailedCourseInfo> mandatoryCourses = candidatePool.stream()
                .filter(course -> mandatoryAndRetakeCodes.contains(course.getCourseCode()))
                .distinct()
                .collect(Collectors.toList());

        if (hasTimeConflictInList(mandatoryCourses)) {
            return null;
        }
        return mandatoryCourses;
    }

    private List<DetailedCourseInfo> filterAndSortByTimePreferences(List<DetailedCourseInfo> courses, TimePreferenceRequest preferences) {
        if (preferences == null) return courses;

        return courses.stream()
                .filter(course -> meetsIndividualCourseTimePreferences(course, preferences))
                // TODO: 선호도에 따른 정렬 로직 추가
                .collect(Collectors.toList());
    }

    private boolean meetsIndividualCourseTimePreferences(DetailedCourseInfo course, TimePreferenceRequest preferences) {
        if (course.getScheduleSlots() == null || course.getScheduleSlots().isEmpty()) return true;

        for (TimeSlotDto slot : course.getScheduleSlots()) {
            if (slot.getDay() == null || slot.getPeriods() == null) continue; // 유효하지 않은 슬롯 스킵

            if (preferences.getAvoidDays() != null && preferences.getAvoidDays().contains(slot.getDay())) {
                return false;
            }
            if (preferences.getAvoidTimeSlots() != null) {
                for (TimeSlotDto avoidSlot : preferences.getAvoidTimeSlots()) {
                    if (avoidSlot.getDay() != null && avoidSlot.getPeriods() != null &&
                            avoidSlot.getDay().equals(slot.getDay()) &&
                            !Collections.disjoint(avoidSlot.getPeriods(), slot.getPeriods())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private List<List<DetailedCourseInfo>> findTimetableCombinations(
            List<DetailedCourseInfo> initialTimetableBase,
            List<DetailedCourseInfo> availableCoursePool, // 전체 후보군 (필수/재수강 포함)
            UserCourseSelectionEntity userSelections, // isRestrictedCourse 필터링 시 사용
            TimePreferenceRequest timePreferences,
            CreditSettingsRequest creditSettings,
            int numRecommendationsNeeded) {

        List<List<DetailedCourseInfo>> validTimetables = new ArrayList<>();
        List<DetailedCourseInfo> currentCombination = new ArrayList<>(initialTimetableBase); // 필수과목으로 시작

        Set<String> initialCourseCodes = initialTimetableBase.stream()
                .map(DetailedCourseInfo::getCourseCode)
                .collect(Collectors.toSet());

        // 실제 선택 가능한 과목들 (초기 시간표에 포함된 것 제외, 특수 제한 과목도 조건부 제외)
        List<DetailedCourseInfo> electivePool = availableCoursePool.stream()
                .filter(c -> !initialCourseCodes.contains(c.getCourseCode())) // 초기 시간표에 이미 있는 과목 제외
                .filter(c -> !(c.isRestrictedCourse() && !initialCourseCodes.contains(c.getCourseCode()))) // 사용자가 직접 고르지 않은 제한과목 제외
                .collect(Collectors.toList());

        // 시간 선호도에 따라 electivePool 정렬 또는 추가 필터링 (선택적)
        electivePool = filterAndSortByTimePreferences(electivePool, timePreferences);

        generateCombinationsRecursive(
                electivePool, 0, currentCombination, validTimetables,
                timePreferences, creditSettings, numRecommendationsNeeded
        );

        return validTimetables.stream().limit(numRecommendationsNeeded).collect(Collectors.toList());
    }

    private void generateCombinationsRecursive(
            List<DetailedCourseInfo> electives, int startIndex,
            List<DetailedCourseInfo> currentCombination,
            List<List<DetailedCourseInfo>> validTimetables,
            TimePreferenceRequest timePreferences,
            CreditSettingsRequest creditSettings,
            int numRecommendationsNeeded) {

        if (validTimetables.size() >= numRecommendationsNeeded) {
            return;
        }

        // 현재 조합이 모든 조건을 만족하는지 (최소 학점 포함)
        if (meetsAllCreditCriteria(currentCombination, creditSettings)) {
            if (meetsTimePreferences(currentCombination, timePreferences)) { // 시간 선호도도 최종 만족하는지
                // 중복된 시간표 조합인지 확인 (과목 코드 Set 비교)
                Set<String> currentCombinationCodes = currentCombination.stream().map(DetailedCourseInfo::getCourseCode).collect(Collectors.toSet());
                boolean isDuplicate = validTimetables.stream()
                        .anyMatch(existing -> existing.stream().map(DetailedCourseInfo::getCourseCode).collect(Collectors.toSet())
                                .equals(currentCombinationCodes));
                if (!isDuplicate) {
                    validTimetables.add(new ArrayList<>(currentCombination));
                    if (validTimetables.size() >= numRecommendationsNeeded) return;
                }
            }
        }

        Map<String, Integer> currentCreditsByType = calculateCreditsByType(currentCombination, creditSettings.getCourseTypeCombination());
        int currentTotalCredits = currentCreditsByType.values().stream().mapToInt(Integer::intValue).sum();

        if (startIndex >= electives.size() ||
                (creditSettings.getMaxTotalCredits() != null && currentTotalCredits >= creditSettings.getMaxTotalCredits())) {
            return;
        }

        for (int i = startIndex; i < electives.size(); i++) {
            DetailedCourseInfo courseToAdd = electives.get(i);

            // 현재 조합에 이미 있는 과목인지 확인 (중복 추가 방지)
            boolean alreadyAdded = currentCombination.stream().anyMatch(c -> c.getCourseCode().equals(courseToAdd.getCourseCode()));
            if (alreadyAdded) continue;

            if (canAddCourseCreditWise(courseToAdd, currentCreditsByType, currentTotalCredits, creditSettings, courseDataService)) {
                currentCombination.add(courseToAdd);
                if (!hasTimeConflictInList(currentCombination)) {
                    generateCombinationsRecursive(electives, i + 1, currentCombination, validTimetables, timePreferences, creditSettings, numRecommendationsNeeded);
                }
                currentCombination.remove(currentCombination.size() - 1); // 백트래킹
            }
            if (validTimetables.size() >= numRecommendationsNeeded) return;
        }
    }

    private boolean hasTimeConflictInList(List<DetailedCourseInfo> courses) {
        if (courses == null || courses.isEmpty()) return false;

        List<TimeSlotDto> allSlots = courses.stream()
                .filter(c -> c.getScheduleSlots() != null)
                .flatMap(course -> course.getScheduleSlots().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (allSlots.isEmpty() && !courses.isEmpty() && courses.stream().anyMatch(c -> c.getScheduleSlots() == null || c.getScheduleSlots().isEmpty())) {
            // 시간 정보가 없는 과목만 있다면 충돌 없음 (정책에 따라 다를 수 있음)
        }


        Map<String, Set<Integer>> dailySchedule = new HashMap<>(); // 중복 체크를 위해 Set 사용
        for (TimeSlotDto slot : allSlots) {
            if (slot.getDay() == null || slot.getPeriods() == null || slot.getPeriods().isEmpty()) continue;

            Set<Integer> periodsForDay = dailySchedule.computeIfAbsent(slot.getDay(), k -> new HashSet<>());
            for (Integer period : slot.getPeriods()) {
                if (!periodsForDay.add(period)) {
                    return true; // 중복 발견
                }
            }
        }
        return false;
    }

    private boolean canAddCourseCreditWise(DetailedCourseInfo courseToAdd,
                                           Map<String, Integer> currentCreditsByType,
                                           int currentTotalCredits,
                                           CreditSettingsRequest creditSettings,
                                           CourseDataService courseDataService) {

        String generalizedType = courseToAdd.getGeneralizedType(); // DetailedCourseInfo에서 미리 계산된 값 사용
        if (generalizedType == null) { // 혹시 generalizedType이 null이면 기본값 처리
            generalizedType = courseDataService.mapDepartmentToGeneralizedType(courseToAdd.getDepartmentOriginal());
        }


        int creditsOfCourseToAdd = courseToAdd.getCredits();
        int newCreditsForType = currentCreditsByType.getOrDefault(generalizedType, 0) + creditsOfCourseToAdd;
        int newTotalCredits = currentTotalCredits + creditsOfCourseToAdd;

        Map<String, CreditRangeDto> rangesPerType = creditSettings.getCreditRangesPerType();
        if (rangesPerType != null) {
            CreditRangeDto typeRange = rangesPerType.get(generalizedType);
            if (typeRange != null && newCreditsForType > typeRange.getMax()) {
                return false;
            }
        }

        if (creditSettings.getMaxTotalCredits() != null && newTotalCredits > creditSettings.getMaxTotalCredits()) {
            return false;
        }

        return true;
    }

    private boolean meetsAllCreditCriteria(List<DetailedCourseInfo> timetable, CreditSettingsRequest creditSettings) {
        if (timetable.isEmpty() && (creditSettings.getMinTotalCredits() == null || creditSettings.getMinTotalCredits() > 0)) {
            // 최소 총 학점 조건이 있는데, 시간표가 비어있으면 만족 못함
            if (creditSettings.getMinTotalCredits() != null && creditSettings.getMinTotalCredits() > 0) return false;
        }
        if (timetable.isEmpty() && (creditSettings.getMinTotalCredits() == null || creditSettings.getMinTotalCredits() == 0)) {
            // 최소 총 학점 조건이 없거나 0이면 빈 시간표도 조건을 만족 (하지만 실제로는 추천하지 않아야 함)
            // 이 함수는 "현재 상태가 유효한가"를 판단. 빈 시간표는 추천 대상이 아니므로 false 반환
            return false;
        }


        Map<String, Integer> currentCreditsByType = calculateCreditsByType(timetable, creditSettings.getCourseTypeCombination());
        int currentTotalCredits = currentCreditsByType.values().stream().mapToInt(Integer::intValue).sum();

        if (creditSettings.getMinTotalCredits() != null && currentTotalCredits < creditSettings.getMinTotalCredits()) return false;
        if (creditSettings.getMaxTotalCredits() != null && currentTotalCredits > creditSettings.getMaxTotalCredits()) return false;

        if (creditSettings.getCourseTypeCombination() != null && creditSettings.getCreditRangesPerType() != null) {
            for (String type : creditSettings.getCourseTypeCombination()) {
                CreditRangeDto range = creditSettings.getCreditRangesPerType().get(type);
                int creditsForThisType = currentCreditsByType.getOrDefault(type, 0);
                if (range != null) {
                    if (creditsForThisType < range.getMin()) return false;
                    // 최대 학점은 canAddCourseCreditWise에서 이미 추가 시점에서 검사됨.
                    // 하지만, 여기서 한번 더 검사해도 무방 (예: 필수과목만으로 이미 최대학점 초과 등)
                    if (creditsForThisType > range.getMax()) return false;
                } else if (creditsForThisType > 0 && creditSettings.getCourseTypeCombination().contains(type)) {
                    // 사용자가 조합에 포함시킨 유형인데, 범위 설정이 없는 경우
                    // 이 유형의 과목이 0학점이어야 한다는 의미는 아님. 범위가 없으면 최소/최대 제한이 없는 것으로 간주.
                }
            }
        }
        return true; // 모든 조건을 통과했고, 비어있지 않은 시간표
    }

    private boolean meetsTimePreferences(List<DetailedCourseInfo> timetable, TimePreferenceRequest preferences) {
        if (preferences == null) return true;

        boolean avoidDaysSet = preferences.getAvoidDays() != null && !preferences.getAvoidDays().isEmpty();
        boolean avoidTimeSlotsSet = preferences.getAvoidTimeSlots() != null && !preferences.getAvoidTimeSlots().isEmpty();
        boolean preferredDaysSet = preferences.getPreferredDays() != null && !preferences.getPreferredDays().isEmpty();
        boolean preferredTimeSlotsSet = preferences.getPreferredTimeSlots() != null && !preferences.getPreferredTimeSlots().isEmpty();

        if (!avoidDaysSet && !avoidTimeSlotsSet && !preferredDaysSet && !preferredTimeSlotsSet &&
                preferences.getPreferNoClassDays() == null &&
                (preferences.getPreferredPeriodBlocks() == null || preferences.getPreferredPeriodBlocks().isEmpty()) ) {
            return true; // 아무런 시간 선호도 설정이 없으면 항상 만족
        }

        for (DetailedCourseInfo course : timetable) {
            if (course.getScheduleSlots() == null || course.getScheduleSlots().isEmpty()) continue;
            for (TimeSlotDto slot : course.getScheduleSlots()) {
                if (slot.getDay() == null || slot.getPeriods() == null) continue;

                if (avoidDaysSet && preferences.getAvoidDays().contains(slot.getDay())) return false;
                if (avoidTimeSlotsSet) {
                    for (TimeSlotDto avoidSlot : preferences.getAvoidTimeSlots()) {
                        if (avoidSlot.getDay() != null && avoidSlot.getPeriods() != null &&
                                avoidSlot.getDay().equals(slot.getDay()) &&
                                !Collections.disjoint(avoidSlot.getPeriods(), slot.getPeriods())) {
                            return false;
                        }
                    }
                }
                // TODO: 선호 요일/시간 부합 여부 (만족 못하면 false 반환하는 로직 추가 가능)
                // 예: 만약 선호 요일이 설정되어 있는데, 이 수업이 선호 요일에 없다면? (엄격하게 할지, 점수화할지 정책 필요)
            }
        }

        // TODO: 전체 시간표 단위의 선호도 검사 (공강일 선호 등)
        if (Boolean.TRUE.equals(preferences.getPreferNoClassDays())) {
            Set<String> classDays = timetable.stream()
                    .filter(c -> c.getScheduleSlots() != null)
                    .flatMap(c -> c.getScheduleSlots().stream())
                    .map(TimeSlotDto::getDay)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            // 예: 5일(월-금) 중 수업일이 3일 이하여야 한다 등 구체적인 정책 필요
            // if (classDays.size() > 3) return false; // 매우 단순한 예시
        }

        return true;
    }

    private Map<String, Integer> calculateCreditsByType(List<DetailedCourseInfo> courses, List<String> userDefinedTypes) {
        Map<String, Integer> creditsMap = new HashMap<>();
        List<String> typesToConsider = new ArrayList<>();

        if (userDefinedTypes != null && !userDefinedTypes.isEmpty()) {
            typesToConsider.addAll(userDefinedTypes);
        } else { // 사용자가 특정 조합 유형을 선택하지 않은 경우, 모든 등장하는 유형을 고려
            courses.stream()
                    .map(DetailedCourseInfo::getGeneralizedType) // 이미 DetailedCourseInfo에 매핑된 타입 사용
                    .filter(Objects::nonNull).distinct()
                    .forEach(typesToConsider::add);
        }
        // 초기화
        for(String type : typesToConsider) creditsMap.put(type, 0);
        // "기타" 유형도 항상 포함하여 계산 (매핑 안 된 과목들 처리)
        if (!creditsMap.containsKey("기타")) {
            creditsMap.put("기타", 0);
        }


        for (DetailedCourseInfo course : courses) {
            String generalizedType = course.getGeneralizedType();
            if (generalizedType == null) generalizedType = "기타"; // 안전장치

            // creditsMap에 해당 유형이 있으면 (사용자가 고려하기로 한 유형이거나, 모든 유형 고려 시) 학점 추가
            if (creditsMap.containsKey(generalizedType)) {
                creditsMap.put(generalizedType, creditsMap.get(generalizedType) + course.getCredits());
            } else { // 고려 대상 유형이 아니지만, 계산에는 포함될 수 있는 "기타" 유형 등
                creditsMap.put("기타", creditsMap.getOrDefault("기타", 0) + course.getCredits());
            }
        }
        return creditsMap;
    }

    private RecommendedTimetableDto convertToRecommendedDto(int id, List<DetailedCourseInfo> courses, Map<String, Integer> creditsByType, int totalCredits) {
        List<ScheduledCourseDto> scheduledCourses = courses.stream()
                .map(course -> new ScheduledCourseDto(
                        course.getCourseCode(),
                        course.getCourseName(),
                        course.getGeneralizedType(),
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