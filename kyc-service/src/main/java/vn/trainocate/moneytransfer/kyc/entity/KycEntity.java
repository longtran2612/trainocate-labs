package vn.trainocate.moneytransfer.kyc.entity;

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

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "kyc_records")
public class KycEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "kyc_id")
    private UUID kycId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "account_no", length = 20)
    private String accountNo;

    @Column(name = "kyc_tier", length = 20, nullable = false)
    @Builder.Default
    private String kycTier = "TIER_0";

    @Column(name = "full_name", length = 200, nullable = false)
    private String fullName;

    @Column(name = "id_number", length = 20, unique = true, nullable = false)
    private String idNumber;

    @Column(name = "id_type", length = 30, nullable = false)
    private String idType;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING";
}
