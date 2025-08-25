@Slf4j
@Service
@RequiredArgsConstructor
class OrganizationGrantServiceImpl implements OrganizationGrantService {
    private final OrganizationGrantRepository organizationGrantRepository;
    private final OrganizationGrantEventService organizationGrantEventService;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OrganizationGrant updateOrganizationGrant(Project project, Organization organization, List<Role> roles) {
        log.debug("Updating grants of project id={} to organization id={} with roles={}", project, organization, roles);

        Set<Long> projectRoles = project.getRoles().stream()
                .map(Role::getId)
                .collect(Collectors.toSet());;

        Map<Long, String> roleIds = roles.stream()
                .collect(Collectors.toMap(Role::getId, Role::getIamId));

        if (!projectRoles.containsAll(roleIds.keySet())) {
            log.debug("Project contains roles={} but does not contain some of the roles to be added={}", projectRoles, roleIds.keySet());
            throw ProjectValidationExceptions.missingProjectRoles();
        }

        Optional<OrganizationGrant> optional = organizationGrantRepository.findById(new OrganizationGrant.OrganizationGrantId(project, organization));
        if (optional.isPresent()) {
            OrganizationGrant organizationGrant = optional.get();
            organizationGrant.setRoles(roleIds.keySet());
            organizationGrant.setStatus(OrganizationGrantStatus.INITIALIZING);
            organizationGrant = organizationGrantRepository.save(organizationGrant);

            log.debug("Updated project grant id={}", organizationGrant);

            organizationGrantEventService.updated(organizationGrant);

            return organizationGrant;
        } else {
            OrganizationGrant organizationGrant = new OrganizationGrant();
            organizationGrant.setProject(project);
            organizationGrant.setOrganization(organization);
            organizationGrant.setRoles(roleIds.keySet());
            organizationGrant.setStatus(OrganizationGrantStatus.INITIALIZING);
            organizationGrant = organizationGrantRepository.save(organizationGrant);

            log.debug("Created project grant id={}", organizationGrant);

            organizationGrantEventService.created(organizationGrant);

            return organizationGrant;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationGrant getOrganizationGrant(Project project, Organization organization) {
        log.debug("Getting organization grant by project id={}, organization id={}", project, organization);

        return organizationGrantRepository.findByProjectAndOrganization(project, organization);
    }
}
