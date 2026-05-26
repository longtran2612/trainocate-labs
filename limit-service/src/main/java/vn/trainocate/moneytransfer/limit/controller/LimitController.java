package vn.trainocate.moneytransfer.limit.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.trainocate.moneytransfer.limit.dto.ApiResponse;
import vn.trainocate.moneytransfer.limit.dto.request.LimitCheckRequest;
import vn.trainocate.moneytransfer.limit.dto.request.LimitConsumeRequest;
import vn.trainocate.moneytransfer.limit.dto.request.LimitInfoRequest;
import vn.trainocate.moneytransfer.limit.dto.response.LimitCheckResponse;
import vn.trainocate.moneytransfer.limit.dto.response.LimitConsumeResponse;
import vn.trainocate.moneytransfer.limit.dto.response.LimitInfoResponse;
import vn.trainocate.moneytransfer.limit.service.LimitService;

@RestController
@RequestMapping("/api/v1/limits")
@RequiredArgsConstructor
public class LimitController {

    private final LimitService limitService;

    @PostMapping("/limit-check")
    public ApiResponse<LimitCheckResponse> limitCheck(@RequestBody LimitCheckRequest request) {
        return ApiResponse.success(limitService.limitCheck(request));
    }

    @PostMapping("/limit-info")
    public ApiResponse<LimitInfoResponse> limitInfo(@RequestBody LimitInfoRequest request) {
        return ApiResponse.success(limitService.limitInfo(request));
    }

    @PostMapping("/limit-consume")
    public ApiResponse<LimitConsumeResponse> limitConsume(@RequestBody LimitConsumeRequest request) {
        return ApiResponse.success(limitService.limitConsume(request));
    }
}
