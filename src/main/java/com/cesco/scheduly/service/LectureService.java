package com.cesco.scheduly.service;

import com.cesco.scheduly.dto.course.LectureDto;
import com.cesco.scheduly.repository.LectureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LectureService {

    private final LectureRepository lectureRepository;

    public List<LectureDto> getAllLectures() {
        return lectureRepository.findAll().stream()
                .map(LectureDto::from)
                .collect(Collectors.toList());
    }
}