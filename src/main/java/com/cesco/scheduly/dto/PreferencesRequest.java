package com.cesco.scheduly.dto;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter

public class PreferencesRequest {
  private List<String> completed_lectures;
  private List<String> required_lectures;
  private List<String> retake_lectures;
}