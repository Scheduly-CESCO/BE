@Getter
public class SignupResponse {
    private Long id;
    private String student_id;
    private String name;
    private LocalDateTime created_at;

    public SignupResponse(User user) {
        this.id = user.getId();
        this.student_id = user.getStudentId();
        this.name = user.getName();
        this.created_at = user.getCreatedAt();
    }
}
