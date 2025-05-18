@Service
public class LectureDataService {

    @Autowired
    private LectureRepository lectureRepository;

    public List<String> findLecturesByPrefix(List<String> prefixes) {
        List<String> allLectureIds = lectureRepository.findAllLectureIds(); // ["CS101-01", ...]
        return allLectureIds.stream()
                .filter(id -> prefixes.stream().anyMatch(prefix -> id.startsWith(prefix)))
                .collect(Collectors.toList());
    }
}
