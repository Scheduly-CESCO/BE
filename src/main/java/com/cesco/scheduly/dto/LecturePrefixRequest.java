package com.cesco.scheduly.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class LecturePrefixRequest {

    @JsonProperty("exclude_prefixes")
    private List<String> exclude_prefixes;
}   //앞자리 6개가 동일한 학수번호 입력 있을 시 요청