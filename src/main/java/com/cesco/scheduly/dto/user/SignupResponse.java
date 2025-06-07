package com.cesco.scheduly.dto.user;

import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.util.GraduationRequirementUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;


@Getter
public class SignupResponse {
    private Long id;

    @JsonProperty("student_id")
    private String studentId;
    private String name;
    private String college;
    private String major;

    @JsonProperty("double_major")
    private String doubleMajor;

    @JsonProperty("admission_year")
    private int admissionYear;

    @JsonProperty("graduation_credits")
    private int graduationCredits;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    private List<String> modules;

    public SignupResponse(User user) {
        this.id = user.getId();
        this.studentId = user.getStudentId();
        this.name = user.getName();
        this.college = user.getCollege();
        this.major = user.getMajor();
        this.doubleMajor = user.getDoubleMajor();
        this.admissionYear = GraduationRequirementUtil.extractAdmissionYear(user.getStudentId());
        this.graduationCredits = GraduationRequirementUtil.getGraduationCredits(user.getCollege(), admissionYear);
        this.createdAt = user.getCreatedAt();

        // User 엔티티의 module 필드를 기반으로 modules 리스트를 동적으로 생성
        List<String> userModules = new ArrayList<>();
        if (user.getModule1() != null) {
            userModules.add(user.getModule1());
        }
        if (user.getModule2() != null) {
            userModules.add(user.getModule2());
        }
        if (user.getModule3() != null) {
            userModules.add(user.getModule3());
        }
        this.modules = userModules;
    }
}
