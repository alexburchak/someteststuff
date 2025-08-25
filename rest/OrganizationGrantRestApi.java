@RestApi(
        name = "organizations",
        description = "Organizations",
        url = "/api/organizations/v1"
)
@RequiredArgsConstructor
class OrganizationGrantRestApi extends AbstractRestApi {
    private final OrganizationService organizationService;
    private final ProjectService projectService;
    private final OrganizationGrantService organizationGrantService;
    private final RoleService roleService;

    @GetMapping(path = "{organizationId}/projects/{projectId}/grants", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize(SecurityExpressions.Admin.MANAGE_ORGANIZATIONS)
    @Operation(description = "Get organization grants")
    @Transactional(readOnly = true)
    public @NotNull List<RoleV1> getOrganizationGrants(
            @Schema(description = "Organization ID")
            @PathVariable(name = "organizationId")
            @Positive long organizationId,
            @Schema(description = "Project ID")
            @PathVariable(name = "projectId")
            @Positive long projectId) {

        Organization parentOrganization = organizationService.getParentOrganization();

        Project project = projectService.getProject(parentOrganization, projectId);
        Organization tenant = organizationService.getOrganization(organizationId);

        OrganizationGrant organizationGrant = organizationGrantService.getOrganizationGrant(project, tenant);

        return roleService.getRoles(project, organizationGrant.getRoles()).stream()
                .map(r -> conversionService.convert(r, RoleV1.class))
                .toList();
    }
}
