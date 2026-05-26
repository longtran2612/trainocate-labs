package vn.trainocate.moneytransfer.limit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account_limits")
public class LimitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "limit_id")
    private UUID limitId;

    @Column(name = "account_no", length = 20, nullable = false)
    private String accountNo;

    @Column(name = "kyc_tier", length = 20, nullable = false)
    private String kycTier;

    @Column(name = "transfer_type", length = 20, nullable = false)
    private String transferType;

    @Column(name = "single_limit", precision = 18, scale = 2, nullable = false)
    private BigDecimal singleLimit;

    @Column(name = "daily_limit", precision = 18, scale = 2, nullable = false)
    private BigDecimal dailyLimit;

    @Column(name = "monthly_limit", precision = 18, scale = 2, nullable = false)
    private BigDecimal monthlyLimit;

    @Column(name = "used_daily", precision = 18, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal usedDaily = BigDecimal.ZERO;

    @Column(name = "used_monthly", precision = 18, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal usedMonthly = BigDecimal.ZERO;

    @Column(name = "reset_at", nullable = false)
    private LocalDateTime resetAt;
}
