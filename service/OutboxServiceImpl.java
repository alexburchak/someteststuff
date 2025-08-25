@Slf4j
@Service
@RequiredArgsConstructor
class OutboxServiceImpl implements OutboxService {
    private final OutboxRepository outboxRepository;
    private final JsonObjectConverterFactory jsonObjectConverterFactory;
    private final TransactionService transactionService;
    private final OutboxEntityEventMessageService outboxEntityEventMessageService;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public <T extends Identifiable<ID>, ID extends Serializable> Outbox createOutbox(EntityEvent<T, ID> event, int partition) {
        log.debug("Creating outbox event type={}", event.getClass().getSimpleName());

        JsonObject<EntityEvent<?, ?>> jsonObject = jsonObjectConverterFactory.create(event);

        Outbox outbox = new Outbox();
        outbox.setEvent(jsonObject);
        outbox.setPartition(partition);

        return outboxRepository.save(outbox);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void processEvents() {
        outboxRepository.findAllNotSent()
                .forEach(o -> {
                    log.debug("Processing outbox event id={}", o);

                    try {
                        outboxEntityEventMessageService.sendMessage(o);

                        // thankfully to @DynamicUpdate on the entity we only update the sendDate, after successful message send to the topic
                        // thus, it is possible the entity processedDate is set earlier than sentDate, which is not a problem
                        // transaction synchronization is not used as it is still not a 2PC
                        transactionService.execute(
                                Propagation.REQUIRES_NEW,
                                () -> {
                                    o.setSentDate(ZonedDateTime.now());
                                    outboxRepository.save(o);

                                    log.debug("Updated outbox event id={} sent date to {}", o, o.getSentDate());
                                }
                        );
                    } catch (Exception e) {
                        log.warn("Event id={} processing failed", o, e);
                    }
                });
    }

    @Override
    @Transactional
    public void updateStatus(long id, OutboxProcessingStatus status) {
        log.debug("Updating outbox id={} status to={}", id, status);

        outboxRepository.findById(id)
                .ifPresentOrElse(
                        o -> {
                            o.setProcessedDate(ZonedDateTime.now());
                            o.setProcessingStatus(status);
                        },
                        () -> log.warn("Outbox id={} not found", id)
                );
    }
}
