package vn.trainocate.moneytransfer.externaltransfer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.trainocate.moneytransfer.externaltransfer.dto.ApiResponse;

import java.util.Map;

@FeignClient(name = "napas-simulator")
public interface NapasClient {

    @PostMapping("/api/v1/napas/inquiry")
    ApiResponse inquiry(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/napas/transfer")
    ApiResponse transfer(@RequestBody Map<String, Object> request);
}
