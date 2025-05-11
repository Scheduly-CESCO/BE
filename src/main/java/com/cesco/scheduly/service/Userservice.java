@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User signup(SignupRequest dto) {
        if (userRepository.existsByStudentId(dto.getStudent_id())) {
            throw new UserAlreadyExistsException();
        }
        User user = new User();
        user.setStudentId(dto.getStudent_id());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setName(dto.getName());
        user.setMajor(dto.getMajor());
        user.setDoubleMajor(dto.getDouble_major());
        user.setGrade(dto.getGrade());
        user.setSemester(dto.getSemester());
        return userRepository.save(user);
    }
}
