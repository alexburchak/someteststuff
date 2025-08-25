@RequiredArgsConstructor
@Component
@Slf4j
@Lazy(false)
class OutboxEntityEventMessageListener {
    private final OutboxService outboxService;
    private final EntityLockService entityLockService;
    private final AuditLogServiceFactory auditLogServiceFactory;

    private Map<Class<? extends EntityEvent<?, ?>>, EntityEventHandler<?>> handlers;

    @Autowired
    void setHandler(List<EntityEventHandler<?>> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(EntityEventHandler::getEventType, Function.identity()));
    }

    @Retryable(
            retryFor = IAMException.class,
            maxAttemptsExpression = "${outbox.retry.max-attempts}",
            backoff = @Backoff(delayExpression = "${outbox.retry.delay-ms}", multiplierExpression = "${outbox.retry.multiplier}"),
            recover = "recover"
    )
    @KafkaListener(topics = "${outbox.topic}", groupId = "${spring.kafka.consumer.group-id}", concurrency = "1")
    @SuppressWarnings("unused")
    public <T extends Identifiable<ID>, ID extends Serializable> void listen(
            @Header(OutboxProperties.HEADER_OUTBOX_ID) long outboxId,
            @Payload EntityEvent<T, ID> event) {

        log.debug("Handling outbox id={} message", outboxId);

        boolean locked = entityLockService.hasLock(event.getEntityType(), event.getEntityId(), EntityLockType.IAM_FAILURE);

        if (locked) {
            log.debug("Entity type={}, entity id={} is locked", event.getEntityType(), event.getEntityId());

            // when lock is being removed, all the outbox events status should be reset, so that the messages are sent to the topic again
            logEvent(event, AuditLogActionStatus.LOCKED, null);

            outboxService.updateStatus(outboxId, OutboxProcessingStatus.LOCKED);
        } else {
            handle(event);

            log.debug("Event processed successfully: {}", event);

            outboxService.updateStatus(outboxId, OutboxProcessingStatus.SUCCESS);
        }
    }

    @Recover
    @Transactional
    @SuppressWarnings("unused")
    public <T extends Identifiable<ID>, ID extends Serializable> void recover(
            @Header(OutboxProperties.HEADER_OUTBOX_ID) long outboxId,
            @Payload EntityEvent<T, ID> event,
            IAMException exception) {

        log.warn("Outbox id={} message could not be handled", outboxId, exception);

        logEvent(event, AuditLogActionStatus.LOCKED, null);

        outboxService.updateStatus(outboxId, OutboxProcessingStatus.LOCKED);
    }

    private void handle(EntityEvent<?, ?> event) {
        for (Class<?> clazz = event.getClass(); clazz != null && clazz != EntityEvent.class; clazz = clazz.getSuperclass()) {
            //noinspection unchecked
            EntityEventHandler<EntityEvent<?, ?>> handler = (EntityEventHandler<EntityEvent<?, ?>>) handlers.get(clazz);

            if (handler != null) {
                log.debug("Found handler for event type: {}", clazz.getSimpleName());

                //noinspection unchecked
                handlers.putIfAbsent((Class<? extends EntityEvent<?, ?>>) event.getClass(), handler);

                try {
                    handler.handle(event);

                    logEvent(event, AuditLogActionStatus.SUCCESS, null);
                } catch (IAMException e) {
                    logEvent(event, AuditLogActionStatus.FAILED, e);
                }

                return;
            }
        }

        throw new IllegalArgumentException("No handler found for event type: " + event.getClass());
    }

    private <T extends Identifiable<ID>, ID extends Serializable> void logEvent(EntityEvent<T, ID> event, AuditLogActionStatus status, IAMException exception) {
        AuditLogService<T, ID, ?> auditLogService = auditLogServiceFactory.getAuditLogService(event.getEntityType());

        if (auditLogService != null) {
            try {
                auditLogService.createAuditLog(event, status, exception);
            } catch (Exception e) {
                log.error("Could not create audit log for entity type={}", event.getEntityType(), e);
            }
        }
    }
}
