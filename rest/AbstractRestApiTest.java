@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestRestConfiguration.class
)
@ComponentScan({
        "com.somestuff"
})
@AutoConfigureMockMvc

@TestExecutionListeners(
        inheritListeners = false,
        listeners = {
                ServletTestExecutionListener.class,
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class,
                WithSecurityContextTestExecutionListener.class,
                MockitoResetTestExecutionListener.class,
                BeanOverrideTestExecutionListener.class
        }
)
public abstract class AbstractRestApiTest extends AbstractServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .addModule(new JavaTimeModule())
            .build();

    @Autowired
    private IAMProperties iamProperties;
    @Autowired
    private UserGrantRepository userGrantRepository;
    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    protected MockMvc mockMvc;

    protected void login(User user, Project project) {
        Set<Long> roleIds = userGrantRepository.findByProjectAndUser(project, user)
                .getRoles();

        List<? extends GrantedAuthority> authorities = roleRepository.findAllById(roleIds).stream()
                .map(Role::getName)
                .map(SimpleGrantedAuthority::new)
                .toList();

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                user.getProfile().getEmail(),
                "password",
                authorities
        ));
    }

    protected <T> ResultMatcher body(TypeReference<T> typeReference, Consumer<T> consumer) {
        return result -> {
            String response = result.getResponse().getContentAsString();

            T object = OBJECT_MAPPER.readerFor(typeReference)
                    .readValue(response);

            consumer.accept(object);
        };
    }

    protected <T> ResultMatcher body(Class<T> type, Consumer<T> consumer) {
        return result -> {
            String response = result.getResponse().getContentAsString();

            T object = OBJECT_MAPPER.readerFor(type)
                    .readValue(response);

            consumer.accept(object);
        };
    }

    protected static ResultMatcher ok() {
        return status().isOk();
    }

    protected static ResultMatcher json() {
        return content().contentType(MediaType.APPLICATION_JSON);
    }
}
