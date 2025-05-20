package com.cesco.scheduly.service;

import com.cesco.scheduly.repository.LectureRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LectureDataService {

    private final LectureRepository lectureRepository;

    public LectureDataService(LectureRepository lectureRepository) {
        this.lectureRepository = lectureRepository;
    }

    public List<String> findLecturesByPrefix(List<String> prefixes) {
        List<String> allLectureIds = lectureRepository.findAllLectureIds();
        return allLectureIds.stream()
                .filter(id -> prefixes.stream().anyMatch(id::startsWith))
                .collect(Collectors.toList());
    }
}