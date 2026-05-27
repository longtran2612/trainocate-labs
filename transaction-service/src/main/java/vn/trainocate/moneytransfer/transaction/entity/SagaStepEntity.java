package vn.trainocate.moneytransfer.transaction.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.trainocate.moneytransfer.transaction.saga.SagaStepName;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "saga_steps")
public class SagaStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "step_id")
    private UUID stepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "saga_id", nullable = false)
    private SagaStateEntity saga;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_name", length = 30, nullable = false)
    private SagaStepName stepName;

    /** COMPLETED, FAILED, COMPENSATED */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;
}
