@RestController
@RequestMapping("/api/preferences")
public class PreferencesController {
  
  @PostMapping
  public ResponseEntity<?> savePreferences(@RequestBody PreferencesRequest dto) {
      TODO: // DB에 최종 저장, 혹은 임시 파일을 저장할 수 있도록
    System.out.println("수강한 과목: " + dto.getCompleted_lectures());
    System.out.println("필수 과목: " + dto.getRequired_lectures());
    System.out.println("재수강 과목: " + dto.getRetake_lectures());
    
    return ResponseEntity.ok(Map.of("status", "saved"));
  }
}
