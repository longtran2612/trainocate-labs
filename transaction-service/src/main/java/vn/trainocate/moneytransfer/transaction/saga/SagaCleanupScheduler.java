package vn.trainocate.moneytransfer.transaction.saga;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class SagaCleanupScheduler {

    private final SagaKafkaOrchestrator sagaKafkaOrchestrator;

    @Scheduled(fixedRate = 60_000)
    public void cleanupStaleSagas() {
        sagaKafkaOrchestrator.cleanupStale(Duration.ofSeconds(60));
    }
}
