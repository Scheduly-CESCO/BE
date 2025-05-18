@RestController
@RequestMapping("/api/lectures")
public class LectureFilterController {

    @Autowired
    private LectureDataService lectureDataService;

    @PostMapping("/exclude-related")
    public ResponseEntity<?> excludeRelated(@RequestBody LecturePrefixRequest req) {
        List<String> result = lectureDataService.findLecturesByPrefix(req.getExclude_prefixes());
        return ResponseEntity.ok(Map.of("excluded", result));
    }
}
