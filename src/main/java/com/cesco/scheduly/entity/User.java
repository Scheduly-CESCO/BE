@Entity
public class User {
    
    @Id @GeneratedValue
    private Long id;

    private String studentId;
    private String passwordHash;
    private String name;
    private String major;
    private String doubleMajor;
    private int grade;
    private int semester;
    private LocalDateTime createdAt = LocalDateTime.now();
}
