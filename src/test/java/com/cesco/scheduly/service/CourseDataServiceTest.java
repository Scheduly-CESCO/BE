package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.course.CourseInfo;
import com.cesco.scheduly.model.DetailedCourseInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS) // @BeforeAll을 non-static으로 사용
@ExtendWith(MockitoExtension.class) // Mockito 기능 활성화 (현재는 @InjectMocks만 사용)
class CourseDataServiceTest {

    @InjectMocks // CourseDataService의 실제 인스턴스를 생성하고 테스트합니다.
    private CourseDataService courseDataService;

    // 테스트용 JSON 파일에 있는 실제 데이터 기반으로 학수번호 설정
    // 학생의 everytime_final.json 내용에 맞춰 수정 필요
    private String sampleCourseCodeMajor = "M01207101"; // 예: 머신러닝 (전공)
    private String sampleCourseCodeGE = "G01001001";    // 예: 교양과목
    private String sampleCourseCodeRestricted = "Y11113E11"; // 예: 신입생세미나

    @BeforeAll
    void setUp() {
        // @PostConstruct가 테스트 환경에서 Spring 컨텍스트 없이 자동으로 호출되지 않을 수 있으므로,
        // 명시적으로 데이터 로딩 메소드를 호출합니다.
        // 이 테스트는 src/main/resources/data/everytime_final.json 파일이 존재하고,
        // 그 내용이 CourseDataService의 로딩 로직과 호환된다고 가정합니다.
        try {
            courseDataService.loadAndProcessCourseData();
        } catch (Exception e) {
            fail("CourseDataService 데이터 로딩 중 예외 발생: " + e.getMessage());
        }
        // 로드된 데이터가 있는지 기본적인 확인
        assertFalse(courseDataService.getDetailedCourses().isEmpty(), "강의 데이터가 로드되지 않았거나 비어있습니다.");
    }

    @Test
    @DisplayName("강의 데이터 로딩 후 groupId 생성 테스트")
    void loadAndProcessCourseData_ShouldCreateGroupId() {
        Optional<DetailedCourseInfo> courseOpt = courseDataService.getDetailedCourses().stream()
                .filter(c -> c.getCourseCode().equals(sampleCourseCodeMajor)) // "M01207101"
                .findFirst();
        assertTrue(courseOpt.isPresent(), sampleCourseCodeMajor + " 과목을 찾을 수 없습니다.");
        courseOpt.ifPresent(course -> {
            assertNotNull(course.getGroupId(), "GroupId가 생성되어야 합니다.");
            assertEquals(sampleCourseCodeMajor.substring(0, 7), course.getGroupId(), "GroupId가 학수번호 앞 7자리와 일치해야 합니다.");
        });
    }

    @Test
    @DisplayName("강의 데이터 로딩 후 generalizedType 1차 분류 테스트")
    void loadAndProcessCourseData_ShouldSetGeneralizedType() {
        // JSON 파일의 실제 "개설영역" 값과 determineInitialGeneralizedType 로직에 따라 기대값 설정
        DetailedCourseInfo majorCourse = courseDataService.getDetailedCourseByCode(sampleCourseCodeMajor); // "개설영역": "전공" 예상
        DetailedCourseInfo geCourse = courseDataService.getDetailedCourseByCode(sampleCourseCodeGE);       // "개설영역": "교양" 또는 관련 키워드 예상
        DetailedCourseInfo restrictedCourse = courseDataService.getDetailedCourseByCode(sampleCourseCodeRestricted); // "개설영역": "신입생세미나" 예상

        assertNotNull(majorCourse, sampleCourseCodeMajor + " 과목 정보 없음");
        assertEquals("전공_후보", majorCourse.getGeneralizedType(), "'전공' 개설영역은 '전공_후보'로 1차 분류되어야 합니다.");

        assertNotNull(geCourse, sampleCourseCodeGE + " 과목 정보 없음");
        assertEquals("교양", geCourse.getGeneralizedType(), "'교양' 관련 개설영역은 '교양'으로 1차 분류되어야 합니다.");

        assertNotNull(restrictedCourse, sampleCourseCodeRestricted + " 과목 정보 없음");
        assertEquals("신입생세미나", restrictedCourse.getGeneralizedType(), "'신입생세미나' 개설영역은 해당 타입으로 1차 분류되어야 합니다.");
        assertTrue(restrictedCourse.isRestrictedCourse(), "'신입생세미나'는 제한 과목으로 처리되어야 합니다.");
    }


    @Test
    @DisplayName("강의 검색 테스트 - 교과목명 '머신러닝' 포함 (실제 데이터 기반)")
    void searchCourses_ByName_Containing_MachineLearning() {
        List<CourseInfo> results = courseDataService.searchCourses("머신러닝", null, null);
        assertNotNull(results);
        // 학생이 제공한 정보: "머신러닝" 포함 과목 3개
        assertEquals(3, results.size(), "JSON 파일 기준 '머신러닝' 포함 과목은 3개여야 합니다.");
        List<String> expectedCodes = Arrays.asList("T04482101", "M01207101", "P05412101");
        List<String> actualCodes = results.stream().map(CourseInfo::getCourseCode).collect(Collectors.toList());
        assertTrue(actualCodes.containsAll(expectedCodes) && expectedCodes.containsAll(actualCodes), "검색된 '머신러닝' 과목 학수번호 불일치");
    }

    @Test
    @DisplayName("강의 검색 테스트 - 교과목명 '알고리즘' 포함 (실제 데이터 기반)")
    void searchCourses_ByName_PartialMatch_Algorithm() {
        List<CourseInfo> results = courseDataService.searchCourses("알고리즘", null, null);
        assertNotNull(results);
        // 학생이 제공한 정보: "알고리즘" 포함 과목 7개
        assertEquals(7, results.size(), "JSON 파일 기준 '알고리즘' 포함 과목은 7개여야 합니다.");
        assertTrue(results.stream().allMatch(c -> c.getCourseName().toLowerCase().contains("알고리즘")));
    }

    @Test
    @DisplayName("강의 검색 테스트 - 개설영역 '교양' (원본 필드 정확히 일치 시, 결과 없음)")
    void searchCourses_ByDepartment_OriginalGeneralEducation_ExpectingEmpty() {
        // 학생 정보: 원본 "개설영역"이 정확히 "교양"인 과목은 없음
        List<CourseInfo> results = courseDataService.searchCourses(null, "교양", null);
        assertNotNull(results);
        assertTrue(results.isEmpty(), "원본 '개설영역'이 정확히 '교양'인 과목은 없어야 합니다.");
    }

    @Test
    @DisplayName("강의 검색 테스트 - 개설영역 '대학외국어' (실제 데이터 확인 필요)")
    void searchCourses_ByDepartment_UniversityForeignLanguage() {
        List<CourseInfo> results = courseDataService.searchCourses(null, "대학외국어", null);
        assertNotNull(results);
        // 학생의 JSON 파일에서 "개설영역": "대학외국어"인 과목의 실제 개수를 확인하여 `expectedCount` 설정
        long expectedCount = courseDataService.getDetailedCourses().stream()
                .filter(c -> "대학외국어".equals(c.getDepartmentOriginal()))
                .count();
        assertEquals(expectedCount, results.size(), "'대학외국어' 과목 검색 결과 개수가 실제 데이터와 일치해야 합니다.");
        if (expectedCount > 0) {
            assertTrue(results.stream().allMatch(c-> "대학외국어".equals(c.getDepartment())));
        }
    }

    @Test
    @DisplayName("강의 검색 테스트 - 학년 '1' (실제 데이터 기반)")
    void searchCourses_ByGrade_FirstYear() {
        // 학생 정보: "학년" 필드는 "null", "1", "2", "3", "4". "전학년" 없음.
        List<CourseInfo> results = courseDataService.searchCourses(null, null, "1");
        assertNotNull(results);

        long expectedCount = courseDataService.getDetailedCourses().stream()
                .filter(c -> "1".equals(c.getGrade())) // CourseInfo.grade는 로딩 시 trim됨
                .map(DetailedCourseInfo::getCourseCode)
                .distinct() // CourseInfo는 학수번호 기준이므로
                .count();

        if (expectedCount > 0) {
            assertFalse(results.isEmpty(), "'1학년' 과목 검색 결과가 없으면 안 됩니다 (JSON 파일에 1학년 과목이 있다면).");
            assertEquals(expectedCount, results.size(), "'1학년' 과목 검색 결과 개수가 실제 데이터와 일치해야 합니다.");
            assertTrue(results.stream().allMatch(c -> c.getGrade() != null && c.getGrade().equals("1")));
        } else {
            assertTrue(results.isEmpty(), "JSON 파일에 '1학년' 과목이 없다면 검색 결과는 비어있어야 합니다.");
        }
        // 특정 1학년 과목 포함 여부 확인 (예: Y11113E11)
        boolean foundSpecific = results.stream().anyMatch(c -> "Y11113E11".equals(c.getCourseCode()));
        if (courseDataService.getCourseInfoByCode("Y11113E11") != null && "1".equals(courseDataService.getCourseInfoByCode("Y11113E11").getGrade())) {
            assertTrue(foundSpecific, "'신입생세미나(자유전공학부)' (Y11113E11) 과목이 검색되어야 합니다.");
        }
    }

    @Test
    @DisplayName("강의 검색 테스트 - 학년 '정보없음' (실제 null 또는 빈 학년 정보)")
    void searchCourses_ByGrade_InfoMissing() {
        List<CourseInfo> results = courseDataService.searchCourses(null, null, "정보없음");
        assertNotNull(results);

        long expectedCount = courseDataService.getDetailedCourses().stream()
                .filter(c -> "정보없음".equals(c.getGrade()))
                .map(DetailedCourseInfo::getCourseCode)
                .distinct()
                .count();

        assertEquals(expectedCount, results.size(), "학년이 '정보없음'인 과목 검색 결과 개수가 실제 데이터와 일치해야 합니다.");
        if (expectedCount > 0) {
            assertTrue(results.stream().allMatch(c -> "정보없음".equals(c.getGrade())));
        }
    }

    @Test
    @DisplayName("determineInitialGeneralizedType 메소드 테스트")
    void determineInitialGeneralizedType_TestCases() {
        assertEquals("전공_후보", courseDataService.determineInitialGeneralizedType("전공"));
        assertEquals("전공_후보", courseDataService.determineInitialGeneralizedType("컴퓨터공학부")); // "학부" 포함 -> "전공_후보" 예상 (현재 로직)
        assertEquals("이중전공", courseDataService.determineInitialGeneralizedType("이중(부)전공")); // "이중" 우선
        assertEquals("이중전공", courseDataService.determineInitialGeneralizedType("이중(제2)"));
        assertEquals("교양", courseDataService.determineInitialGeneralizedType("교양"));
        assertEquals("교양", courseDataService.determineInitialGeneralizedType("대학외국어"));
        assertEquals("교직", courseDataService.determineInitialGeneralizedType("교직"));
        assertEquals("신입생세미나", courseDataService.determineInitialGeneralizedType("신입생세미나"));
        assertEquals("군사학", courseDataService.determineInitialGeneralizedType("군사학"));
        assertEquals("기타", courseDataService.determineInitialGeneralizedType("알수없는영역"));
        assertEquals("기타", courseDataService.determineInitialGeneralizedType(null));
        assertEquals("기타", courseDataService.determineInitialGeneralizedType("  "));
    }
}