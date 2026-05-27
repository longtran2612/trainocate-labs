package vn.trainocate.moneytransfer.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.trainocate.moneytransfer.transaction.dto.ApiResponse;

import java.util.Map;

@FeignClient(name = "kyc-service")
public interface KycClient {

    @PostMapping("/api/v1/kyc/status")
    ApiResponse getKycStatus(@RequestBody Map<String, Object> request);
}
