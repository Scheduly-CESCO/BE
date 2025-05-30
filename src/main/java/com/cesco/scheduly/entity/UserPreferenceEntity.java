package com.cesco.scheduly.entity;

import com.cesco.scheduly.dto.timetable.CreditSettingsRequest;
import com.cesco.scheduly.dto.timetable.TimePreferenceRequest;
import com.cesco.scheduly.util.CreditSettingsRequestConverter; // 이전 파트 1에서 정의
import com.cesco.scheduly.util.TimePreferenceRequestConverter;   // 이전 파트 1에서 정의
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "preference_id")
    private String preferenceId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false, unique = true)
    private User user;

    @Lob
    @Convert(converter = TimePreferenceRequestConverter.class)
    @Column(name = "time_preferences_json", columnDefinition = "TEXT")
    @Builder.Default // 빌더 사용 시 기본값 설정
    private TimePreferenceRequest timePreferences = new TimePreferenceRequest();

    @Lob
    @Convert(converter = CreditSettingsRequestConverter.class)
    @Column(name = "credit_settings_json", columnDefinition = "TEXT")
    @Builder.Default
    private CreditSettingsRequest creditSettings = new CreditSettingsRequest();
}