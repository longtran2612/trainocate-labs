package vn.trainocate.moneytransfer.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.trainocate.moneytransfer.transaction.dto.ApiResponse;

import java.util.Map;

@FeignClient(name = "limit-service")
public interface LimitClient {

    @PostMapping("/api/v1/limits/limit-check")
    ApiResponse limitCheck(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/limits/limit-consume")
    ApiResponse limitConsume(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/limits/limit-release")
    ApiResponse limitRelease(@RequestBody Map<String, Object> request);
}
