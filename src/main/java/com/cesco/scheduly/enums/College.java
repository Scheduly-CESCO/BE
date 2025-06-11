package com.cesco.scheduly.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;

    public enum College {
        통번역대학,
        인문대학,
        국제지역대학,
        경상대학,
        국가전략언어대학,
        글로벌스포츠산업학부,
        AI융합대학,
        기후변화융합학부,
        Culture_Technology융합대학,
        자연과학대학,
        공과대학,
        바이오메디컬공학부,
        자유전공학부,
        융합인재대학;

        @JsonCreator
        public static College from(String value) {
            return Arrays.stream(values())
                    .filter(c -> c.name().replace("_", "&").equalsIgnoreCase(value) || c.name().equalsIgnoreCase(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid College value: " + value));
        }
    }