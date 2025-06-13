package com.cesco.scheduly.entity;

import com.cesco.scheduly.dto.timetable.CreditSettingsRequest;
import com.cesco.scheduly.dto.timetable.TimePreferenceRequest;
import com.cesco.scheduly.util.CreditSettingsRequestConverter;
import com.cesco.scheduly.util.TimePreferenceRequestConverter;
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
    @GeneratedValue(strategy = GenerationType.UUID) // 또는 GenerationType.IDENTITY
    @Column(name = "preference_id")
    private String preferenceId; // UUID 또는 Long 타입 고려

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, unique = true) // User 엔티티의 PK 'id' 참조
    private User user; // UserEntity 대신 User 사용

    @Lob
    @Convert(converter = TimePreferenceRequestConverter.class)
    @Column(name = "time_preferences_json", columnDefinition = "TEXT")
    @Builder.Default
    private TimePreferenceRequest timePreferences = new TimePreferenceRequest();

    @Lob
    @Convert(converter = CreditSettingsRequestConverter.class)
    @Column(name = "credit_settings_json", columnDefinition = "TEXT")
    @Builder.Default
    private CreditSettingsRequest creditSettings = new CreditSettingsRequest();

    @Lob
    @Column(name = "saved_timetable_json", columnDefinition = "TEXT")
    private String savedTimetableJson; // 저장된 시간표(RecommendedTimetableDto)를 JSON 문자열로 저장
}