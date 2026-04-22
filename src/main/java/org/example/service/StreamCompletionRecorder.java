package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class StreamCompletionRecorder {

    private static final Logger log = LoggerFactory.getLogger(StreamCompletionRecorder.class);

    private final JdbcRequestRecordRepository jdbcRequestRecordRepository;
    private final RabbitRequestCompletedPublisher rabbitRequestCompletedPublisher;

    public StreamCompletionRecorder(
            JdbcRequestRecordRepository jdbcRequestRecordRepository,
            RabbitRequestCompletedPublisher rabbitRequestCompletedPublisher
    ) {
        this.jdbcRequestRecordRepository = jdbcRequestRecordRepository;
        this.rabbitRequestCompletedPublisher = rabbitRequestCompletedPublisher;
    }

    public void record(StreamCompletionRecord record) {
        Mono.fromRunnable(() -> doRecord(record))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        ignored -> {
                        },
                        ex -> log.warn("requestId={} requestCompletedRecordFailed reason={}", record.requestId(), ex.getMessage())
                );
    }

    private void doRecord(StreamCompletionRecord record) {
        jdbcRequestRecordRepository.save(record);
        rabbitRequestCompletedPublisher.publish(record);
    }
}
