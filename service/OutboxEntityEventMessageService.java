@RequiredArgsConstructor
@Service
@Slf4j
public class OutboxEntityEventMessageService {
    private final KafkaTemplate<String, EntityEvent<?, ?>> kafkaTemplate;
    private final OutboxProperties outboxProperties;

    public void sendMessage(Outbox outbox) {
        JsonObject<EntityEvent<?, ?>> jsonObject = outbox.getEvent();
        EntityEvent<?, ?> event = jsonObject.getValue();

        log.debug("Sending message {}, entityId={}", event.getClass().getSimpleName(), event.getEntityId());

        Message<? extends EntityEvent<?, ?>> message = MessageBuilder.withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, outboxProperties.getTopic())
                .setHeader(KafkaHeaders.KEY, getKey(outbox))
                .setHeader(KafkaHeaders.PARTITION, outbox.getPartition())
                .setHeader(OutboxProperties.HEADER_OUTBOX_ID, outbox.getId())
                .build();

        kafkaTemplate.send(message);

        log.debug("Message sent successfully");
    }

    private String getKey(Outbox outbox) {
        JsonObject<EntityEvent<?, ?>> jsonObject = outbox.getEvent();
        EntityEvent<?, ?> event = jsonObject.getValue();

        return event.getEntityType().getSimpleName() + "-" + event.getEntityId();
    }
}
