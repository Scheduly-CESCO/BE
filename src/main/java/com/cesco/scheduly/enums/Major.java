package com.cesco.scheduly.enums;
import java.util.*;

public enum Major {
    철학과(College.인문대학),
    사학과(College.인문대학),
    언어인지과학과(College.인문대학),
    지식컨텐츠학부(College.인문대학),
    영어통번역학부(College.통번역대학),
    독일어통번역학과(College.통번역대학),
    스페인어통번역학과(College.통번역대학),
    이탈리아어통번역학과(College.통번역대학),
    중국어통번역학과(College.통번역대학),
    일본어통번역학과(College.통번역대학),
    아랍어통번역학과(College.통번역대학),
    말레이_인도네시아어통번역학과(College.통번역대학),
    태국어통번역학과(College.통번역대학),
    폴란드학과(College.국가전략언어대학),
    루마니아학과(College.국가전략언어대학),
    체코_슬로바키아학과(College.국가전략언어대학),
    헝가리학과(College.국가전략언어대학),
    세르비아_크로아티아학과(College.국가전략언어대학),
    그리스_불가리아학과(College.국가전략언어대학),
    중앙아시아학과(College.국가전략언어대학),
    아프리카학부(College.국가전략언어대학),
    우크라이나학과(College.국가전략언어대학),
    한국학과(College.국가전략언어대학),
    프랑스학과(College.국제지역대학),
    브라질학과(College.국제지역대학),
    인도학과(College.국제지역대학),
    러시아학과(College.국제지역대학),
    국제경영학과(College.경상대학),
    경제학과(College.경상대학),
    경영정보학과(College.경상대학),
    Global_Business_Technology학부(College.경상대학),
    국제금융학과(College.경상대학),
    수학과(College.자연과학대학),
    통계학과(College.자연과학대학),
    전자물리학과(College.자연과학대학),
    환경학과(College.자연과학대학),
    생명공학과(College.자연과학대학),
    화학과(College.자연과학대학),
    컴퓨터공학부(College.공과대학),
    정보통신공학과(College.공과대학),
    반도체전자공학부_반도체공학전공(College.공과대학),
    반도체전자공학부_전자공학전공(College.공과대학),
    산업경영공학과(College.공과대학),
    융합인재학부(College.융합인재대학),
    글로벌스포츠산업학부(College.Culture_Technology융합대학),
    디지털콘텐츠학부(College.Culture_Technology융합대학),
    투어리즘_웰니스학부(College.Culture_Technology융합대학),
    AI데이터융합학부(College.AI융합대학),
    Finance_AI융합학부(College.AI융합대학),
    바이오메디컬공학부(College.바이오메디컬공학부),
    기후변화융합학부(College.기후변화융합학부),
    자유전공학부(College.자유전공학부);

    private final College college;

    Major(College college) {
        this.college = college;
    }

    public College getCollege() {
        return college;
    }

    public static List<String> getMajorsByCollege(College college) {
        List<String> majors = new ArrayList<>();
        for (Major m : Major.values()) {
            if (m.getCollege() == college) {
                majors.add(m.name());
            }
        }
        return majors;
    }
}