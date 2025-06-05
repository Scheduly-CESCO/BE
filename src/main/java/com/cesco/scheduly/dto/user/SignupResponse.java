package com.cesco.scheduly.dto.user;

import com.cesco.scheduly.entity.User;
import com.cesco.scheduly.util.GraduationRequirementUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import java.time.LocalDateTime;

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

    private String module1;
    private String module2;
    private String module3;

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
        this.module1 = user.getModule1();
        this.module2 = user.getModule2();
        this.module3 = user.getModule3();
    }
}
