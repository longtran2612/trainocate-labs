package vn.trainocate.moneytransfer.account.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Debezium CDC payload for the "accounts" table after applying
 * the {@code ExtractNewRecordState} SMT (envelope unwrapped).
 *
 * <p>Connector config:
 * <ul>
 *   <li>{@code decimal.handling.mode=string} → balance fields arrive as String</li>
 *   <li>{@code time.precision.mode=connect} → timestamps as epoch millis (Long)</li>
 *   <li>INSERT / UPDATE → full "after" record as flat JSON</li>
 *   <li>DELETE → tombstone (null message value)</li>
 * </ul>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DebeziumAccountPayload {

    @JsonProperty("account_no")
    private String accountNo;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("cif")
    private String cif;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("address")
    private String address;

    @JsonProperty("mobile")
    private String mobile;

    @JsonProperty("email")
    private String email;

    /** Numeric as String — connector config: decimal.handling.mode=string */
    @JsonProperty("balance")
    private String balance;

    @JsonProperty("available_balance")
    private String availableBalance;

    @JsonProperty("hold_balance")
    private String holdBalance;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("status")
    private String status;
}
