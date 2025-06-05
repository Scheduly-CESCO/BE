package com.cesco.scheduly.util;

public class GraduationRequirementUtil {

    public static int extractAdmissionYear(String studentId) {
        try {
            return Integer.parseInt(studentId.substring(0, 4));
        } catch (Exception e) {
            throw new IllegalArgumentException("학번 형식이 올바르지 않습니다.");
        }
    }

    public static int getGraduationCredits(String college, int admissionYear) {
        if (admissionYear >= 2025) {
            return "융합인재대학".equals(college) ? 150 : 126;
        } else if (admissionYear >= 2023) {
            return switch (college) {
                case "통번역대학", "융합인재대학" -> 150;
                case "인문대학", "동유럽대학", "국제지역대학", "경상대학", "글로벌스포츠산업학부" -> 134;
                case "AI융합대학", "기후변화융합학부", "Culture&Technology융합대학", "자연과학대학", "공과대학", "바이오메디컬공학부" -> 134;
                default -> 134; // 보수적 기본값
            };
        } else if (admissionYear >= 2015) {
            return switch (college) {
                case "통번역대학", "융합인재대학" -> 150;
                case "인문대학", "동유럽대학", "국제지역대학", "경상대학", "글로벌스포츠산업학부", "공과대학", "바이오메디컬공학부", "자연과학대학" -> 134;
                default -> 134;
            };
        } else {
            return 134; // 예외 처리
        }
    }
}