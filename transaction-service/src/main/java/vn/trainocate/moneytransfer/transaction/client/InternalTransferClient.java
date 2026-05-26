package vn.trainocate.moneytransfer.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.trainocate.moneytransfer.transaction.dto.ApiResponse;

import java.util.Map;

@FeignClient(name = "internal-transfer-service")
public interface InternalTransferClient {

    @PostMapping("/api/v1/internal/inquiry")
    ApiResponse inquiry(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/internal/transfer")
    ApiResponse transfer(@RequestBody Map<String, Object> request);
}
