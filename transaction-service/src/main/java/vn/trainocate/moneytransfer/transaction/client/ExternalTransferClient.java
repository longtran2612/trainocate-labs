package vn.trainocate.moneytransfer.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.trainocate.moneytransfer.transaction.dto.ApiResponse;

import java.util.Map;

@FeignClient(name = "external-transfer-service")
public interface ExternalTransferClient {

    @PostMapping("/api/v1/external/inquiry")
    ApiResponse inquiry(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/external/transfer")
    ApiResponse transfer(@RequestBody Map<String, Object> request);
}
