package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.course.CourseInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(MockitoExtension.class)
class CourseDataServiceTest {

    // @InjectMocks 대신 @Spy를 사용하여 실제 객체의 일부 동작을 유지하거나 변경
    // 하지만 이 경우 @PostConstruct가 자동으로 호출되지 않을 수 있으므로,
    // 로딩 로직을 직접 테스트하거나, searchCourses 테스트를 위해 내부 상태를 수동 설정합니다.
    @Spy // 실제 CourseDataService 객체를 생성. PostConstruct는 테스트 환경에 따라 동작이 다를 수 있음.
    @InjectMocks // @Spy와 함께 사용하여 Mock 객체 주입도 가능 (필요시)
    private CourseDataService courseDataService;

    // CourseDataService 내부의 courseCatalogForSearch를 직접 제어하기 위해 Map을 준비
    private Map<String, CourseInfo> testCourseCatalog;

    @BeforeEach
    void setUp() {
        // 테스트용 데이터로 courseCatalogForSearch를 직접 초기화
        testCourseCatalog = new ConcurrentHashMap<>();
        CourseInfo course1 = new CourseInfo("CS101", "Introduction to Programming", "전공", 3, "1");
        CourseInfo course2 = new CourseInfo("MA202", "Calculus I", "교양", 3, "1");
        CourseInfo course3 = new CourseInfo("HIS101", "World History", "교양", 2, "전학년");
        CourseInfo course4 = new CourseInfo("CSNULL", null, "전공", 3, "2"); // 이름이 null인 경우 테스트
        CourseInfo course5 = new CourseInfo(null, "No Code Course", "교양", 3, "2"); // 코드가 null인 경우 테스트

        testCourseCatalog.put(course1.getCourseCode(), course1);
        testCourseCatalog.put(course2.getCourseCode(), course2);
        testCourseCatalog.put(course3.getCourseCode(), course3);
        // course4, course5와 같이 null 값을 가진 데이터는 search 로직에서 문제를 일으킬 수 있으므로,
        // CourseDataService의 searchCourses 로직에서 null 체크가 중요합니다.
        // 또는 CourseDataService 로딩 시점에 이런 데이터를 필터링해야 합니다.
        // 현재 CourseDataService는 이미 필터링 로직이 있으므로, 여기에 null 값을 가진 CourseInfo를 넣으면 안됩니다.
        // 만약 넣고 테스트한다면 searchCourses의 방어 로직을 검증하는 테스트가 됩니다.

        // CourseDataService의 courseCatalogForSearch 필드를 테스트용 Map으로 교체 (리플렉션 사용 또는 테스트용 setter 필요)
        // 여기서는 courseDataService가 실제 JSON을 로드하고, 그 결과를 기반으로 테스트한다고 가정하고,
        // loadAndProcessCourseData가 올바르게 courseCatalogForSearch를 채웠다고 가정하겠습니다.
        // 만약 loadAndProcessCourseData 자체를 단위 테스트하고 싶다면, 파일 I/O를 모킹해야 합니다.

        // 가장 확실한 단위 테스트 방법은 @PostConstruct 로직을 테스트에서는 비활성화하고,
        // 테스트 데이터를 수동으로 주입하는 것입니다.
        // 예시: courseDataService의 내부 상태를 직접 설정할 수 있는 package-private 메소드를 만들거나,
        // @Spy와 doReturn을 사용하여 searchCourses가 사용할 catalog를 제어합니다.

        // 여기서는 courseDataService의 loadAndProcessCourseData()가 실행되어
        // 실제 src/main/resources/data/everytime_courses.json 또는
        // src/test/resources/data/everytime_courses.json (만약 있다면 우선순위)
        // 파일을 읽어 courseCatalogForSearch가 채워진다고 가정하고 진행합니다.
        // 이 테스트가 성공하려면 JSON 파일에 "Introduction to Programming" 등의 데이터가 있어야 합니다.
        courseDataService.loadAndProcessCourseData(); // @PostConstruct가 실행되도록 명시적 호출 (테스트 환경에 따라 동작 보장 안 될 수 있음)
        // 혹은 @SpringBootTest 환경에서는 자동 실행됨.
        // JUnit5 + MockitoExtension 만으로는 @PostConstruct 보장 안됨.
        // 따라서 아래 searchCourses 테스트는 실제 파일 내용에 매우 의존적임.
    }

    @Test
    @DisplayName("강의 검색 테스트 - 교과목명 일치")
    void searchCourses_ByName_ExactMatch() {
        // 실제 JSON 파일에 "머신러닝" 과목이 있다고 가정
        List<CourseInfo> results = courseDataService.searchCourses("머신러닝", null, null);
        assertFalse(results.isEmpty(), "머신러닝 검색 결과가 없습니다. JSON 파일을 확인하세요.");
        assertTrue(results.stream().anyMatch(c -> "머신러닝".equals(c.getCourseName())));
    }

    @Test
    @DisplayName("강의 검색 테스트 - 교과목명 부분 일치")
    void searchCourses_ByName_PartialMatch() {
        // 실제 JSON 파일에 "프로그래밍"을 포함하는 과목이 있다고 가정 (예: 고급파이썬프로그래밍)
        List<CourseInfo> results = courseDataService.searchCourses("프로그래밍", null, null);
        assertFalse(results.isEmpty(), "프로그래밍 포함 검색 결과가 없습니다.");
        assertTrue(results.stream().anyMatch(c -> c.getCourseName().contains("프로그래밍")));
    }

    @Test
    @DisplayName("강의 검색 테스트 - 개설영역 일치")
    void searchCourses_ByDepartment() {
        // 실제 JSON 파일에 "전공" 영역 과목이 있다고 가정
        List<CourseInfo> results = courseDataService.searchCourses(null, "전공", null);
        assertFalse(results.isEmpty(), "전공 검색 결과가 없습니다.");
        assertTrue(results.stream().allMatch(c -> c.getDepartment().equals("전공")));
    }

    @Test
    @DisplayName("강의 검색 테스트 - 학년 일치")
    void searchCourses_ByGrade() {
        // 실제 JSON 파일에 "2"학년 과목이 있다고 가정
        List<CourseInfo> results = courseDataService.searchCourses(null, null, "2");
        assertFalse(results.isEmpty(), "2학년 검색 결과가 없습니다.");
        assertTrue(results.stream().allMatch(c -> c.getGrade().contains("2") || c.getGrade().equals("전학년")));
    }

    @Test
    @DisplayName("강의 검색 테스트 - 결과 없음")
    void searchCourses_NoResults() {
        List<CourseInfo> results = courseDataService.searchCourses("매우특이하고없는과목명12345", null, null);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("강의 검색 테스트 - 쿼리가 null 또는 빈 문자열일 때 모든 결과 반환 (또는 특정 정책)")
    void searchCourses_NullOrEmptyQuery() {
        List<CourseInfo> resultsWithNullQuery = courseDataService.searchCourses(null, null, null);
        List<CourseInfo> resultsWithEmptyQuery = courseDataService.searchCourses("", null, null);

        // 현재 searchCourses는 query가 null이거나 비면 nameMatch가 true가 되므로 전체 결과 반환
        assertFalse(resultsWithNullQuery.isEmpty(), "Null 쿼리 시 결과가 있어야 합니다 (courseCatalogForSearch 크기만큼).");
        assertEquals(courseDataService.courseCatalogForSearch.size(), resultsWithNullQuery.size());
        assertEquals(courseDataService.courseCatalogForSearch.size(), resultsWithEmptyQuery.size());
    }


    @Test
    @DisplayName("개설영역 일반화 타입 매핑 테스트")
    void mapDepartmentToGeneralizedType_Test() {
        assertEquals("전공", courseDataService.mapDepartmentToGeneralizedType("전공필수"));
        assertEquals("이중전공", courseDataService.mapDepartmentToGeneralizedType("이중(부)전공"));
        assertEquals("교양", courseDataService.mapDepartmentToGeneralizedType("교양선택"));
        assertEquals("교직", courseDataService.mapDepartmentToGeneralizedType("교직"));
        assertEquals("기타", courseDataService.mapDepartmentToGeneralizedType("특별활동"));
        assertEquals("기타", courseDataService.mapDepartmentToGeneralizedType(null));
    }
}