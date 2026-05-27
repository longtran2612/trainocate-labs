package vn.trainocate.moneytransfer.napas.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.trainocate.moneytransfer.napas.dto.NapasInquiryRequest;
import vn.trainocate.moneytransfer.napas.dto.NapasInquiryResponse;
import vn.trainocate.moneytransfer.napas.dto.NapasTransferRequest;
import vn.trainocate.moneytransfer.napas.dto.NapasTransferResponse;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
public class NapasSimulatorService {

    private static final Map<String, String> BANK_NAMES = Map.of(
            "VCB", "Vietcombank",
            "TCB", "Techcombank",
            "MBB", "MBBank",
            "ACB", "ACB",
            "BID", "BIDV",
            "CTG", "VietinBank",
            "STB", "Sacombank",
            "VPB", "VPBank",
            "TPB", "TPBank",
            "HDB", "HDBank"
    );

    // Mock account names per bank — simulate real NAPAS name lookup
    private static final Map<String, String> MOCK_ACCOUNTS = Map.of(
            "VCB-9000000001", "TRAN VAN HUNG",
            "VCB-9000000002", "LE THI MAI",
            "TCB-9000000001", "PHAM DUC ANH",
            "MBB-9000000001", "NGUYEN HOANG NAM",
            "BID-9000000001", "VU THI HONG",
            "ACB-9000000001", "DO MINH TUAN"
    );

    private final Random random = new Random();

    @Value("${napas.simulator.delay-ms:500}")
    private long delayMs;

    @Value("${napas.simulator.failure-rate:0.0}")
    private double failureRate;

    public NapasInquiryResponse inquiry(NapasInquiryRequest request) {
        log.info("NAPAS Inquiry: bankCode={}, accountNo={}", request.getBankCode(), request.getAccountNo());
        simulateDelay();

        String bankCode = request.getBankCode() != null ? request.getBankCode().toUpperCase() : "UNKNOWN";
        String bankName = BANK_NAMES.getOrDefault(bankCode, bankCode + " Bank");

        // Lookup mock account name
        String lookupKey = bankCode + "-" + request.getAccountNo();
        String accountName = MOCK_ACCOUNTS.get(lookupKey);

        if (accountName == null) {
            // Generate a deterministic mock name based on account number
            accountName = generateMockName(request.getAccountNo());
        }

        log.info("NAPAS Inquiry result: accountNo={}, name={}, bank={}", request.getAccountNo(), accountName, bankName);

        return NapasInquiryResponse.builder()
                .accountNo(request.getAccountNo())
                .accountName(accountName)
                .bankCode(bankCode)
                .bankName(bankName)
                .responseCode("00")
                .responseMessage("Success")
                .build();
    }

    public NapasTransferResponse transfer(NapasTransferRequest request) {
        log.info("NAPAS Transfer: ref={}, sender={}@{}, receiver={}@{}, amount={}",
                request.getReferenceId(),
                request.getSenderAccountNo(), request.getSenderBankCode(),
                request.getReceiverAccountNo(), request.getReceiverBankCode(),
                request.getAmount());
        simulateDelay();

        // Simulate random failure based on configured rate
        if (random.nextDouble() < failureRate) {
            String napasRef = "NAPAS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.warn("NAPAS Transfer FAILED (simulated): ref={}, napasRef={}", request.getReferenceId(), napasRef);
            return NapasTransferResponse.builder()
                    .napasRef(napasRef)
                    .referenceId(request.getReferenceId())
                    .responseCode("68")
                    .responseMessage("Transaction timeout - simulated failure")
                    .processedAt(LocalDateTime.now())
                    .build();
        }

        String napasRef = "NAPAS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String receiverName = request.getReceiverName();
        if (receiverName == null || receiverName.isBlank()) {
            receiverName = generateMockName(request.getReceiverAccountNo());
        }

        log.info("NAPAS Transfer SUCCESS: ref={}, napasRef={}, receiverName={}",
                request.getReferenceId(), napasRef, receiverName);

        return NapasTransferResponse.builder()
                .napasRef(napasRef)
                .referenceId(request.getReferenceId())
                .responseCode("00")
                .responseMessage("Transaction successful")
                .receiverName(receiverName)
                .processedAt(LocalDateTime.now())
                .build();
    }

    private void simulateDelay() {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String generateMockName(String accountNo) {
        if (accountNo == null || accountNo.length() < 2) return "NGUYEN VAN A";
        // Deterministic name based on last digit
        String[] names = {
                "NGUYEN VAN TOAN", "TRAN THI BICH", "LE HOANG LONG",
                "PHAM MINH DUC", "VO THI THANH", "HOANG VAN HAI",
                "DANG THI LAN", "BUI QUOC VIET", "DO THI HUONG", "NGO XUAN TRUONG"
        };
        int idx = Character.getNumericValue(accountNo.charAt(accountNo.length() - 1));
        return names[Math.abs(idx) % names.length];
    }
}
