package vn.trainocate.moneytransfer.transaction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import vn.trainocate.moneytransfer.transaction.saga.SagaStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "saga_state")
public class SagaStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "saga_id")
    private UUID sagaId;

    @Column(name = "tx_id", nullable = false)
    private UUID txId;

    @Column(name = "saga_type", length = 30, nullable = false)
    private String sagaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 25, nullable = false)
    private SagaStatus status;

    @Column(name = "failed_step", length = 30)
    private String failedStep;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "saga", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("executedAt ASC")
    @Builder.Default
    private List<SagaStepEntity> steps = new ArrayList<>();
}
