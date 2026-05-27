package vn.trainocate.moneytransfer.externaltransfer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.trainocate.moneytransfer.externaltransfer.dto.ApiResponse;

import java.util.Map;

@FeignClient(name = "account-service")
public interface AccountClient {

    @PostMapping("/api/v1/accounts/get-customer-info")
    ApiResponse getCustomerInfo(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/accounts/check-balance")
    ApiResponse checkBalance(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/accounts/debit")
    ApiResponse debit(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/accounts/credit")
    ApiResponse credit(@RequestBody Map<String, Object> request);
}
