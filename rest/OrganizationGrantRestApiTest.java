public class OrganizationGrantRestApiTest extends AbstractRestApiTest {
    @Autowired
    private OrganizationGrantRepository organizationGrantRepository;

    private Organization parentOrganization;

    @BeforeClass
    public void setUpClass() {
        parentOrganization = createparentOrganization();
    }

    @Test
    public void testGetOrganizationGrants() throws Exception {
        // project and its roles
        Project project = project().create();
        Role adminRole = role().create(r -> r.setName(AdminRoles.Management.MANAGE_ORGANIZATIONS));
        Role testRole = role().create(r -> r.setName("TEST_ROLE"));

        // parent admin
        User admin = user().create();
        userGrant().create(ug -> ug.addRole(adminRole));

        // tenant
        Organization tenant = organization().create();
        organizationGrant().create(og -> og.addRole(testRole));

        login(admin, project);

        mockMvc.perform(
                        get("/api/organizations/v1/{organizationId}/projects/{projectId}/grants", tenant.getId(), project.getId())
                )
                .andExpect(ok())
                .andExpect(json())
                .andExpect(body(new TypeReference<List<RoleV1>>() {}, roleV1s -> {
                    assertEquals(roleV1s.size(), 1);

                    RoleV1 roleV1 = roleV1s.getFirst();
                    assertEquals(roleV1.getName(), testRole.getName());

                    OrganizationGrant organizationGrant = organizationGrantRepository.findByProjectAndOrganization(project, tenant);
                    assertNotNull(organizationGrant);
                    assertEquals(organizationGrant.getProject().getId(), project.getId());
                    assertEquals(organizationGrant.getOrganization().getId(), tenant.getId());
                }));
    }
}
