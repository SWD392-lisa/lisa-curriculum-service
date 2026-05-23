package com.lisa.reposervice.controller;

import com.lisa.reposervice.config.LisaServicesConfig;
import com.lisa.reposervice.dto.*;
import com.lisa.reposervice.service.RepoProvisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoController {

    private final RepoProvisionService provisionService;
    private final LisaServicesConfig lisaServicesConfig;

    @Tag(name = "Provision")
    @Operation(
        summary = "Tạo TẤT CẢ LISA repos (6 services × 2 platforms = 12 repos)",
        description = """
            Tự động tạo repo cho toàn bộ LISA microservices trên **GitHub + GitLab** cùng lúc.
            
            Danh sách services sẽ được tạo:
            - lisa-realtime-service
            - lisa-lms-service
            - lisa-user-payment-service
            - lisa-mobile-app
            - lisa-curriculum-service
            - lisa-api-gateway
            
            Nếu repo đã tồn tại → skip (không lỗi).
            """
    )
    @ApiResponse(responseCode = "200", description = "Kết quả provision (success/fail từng repo)")
    @PostMapping("/provision/all")
    public ResponseEntity<ProvisionResult> provisionAll(
            @Parameter(description = "true = tạo private repo") 
            @RequestParam(defaultValue = "true") boolean privateRepo,
            @Parameter(description = "true = tự tạo README.md")
            @RequestParam(defaultValue = "true") boolean initReadme) {

        return ResponseEntity.ok(provisionService.provisionAllLisaServices(privateRepo, initReadme));
    }

    @Tag(name = "Provision")
    @Operation(
        summary = "Tạo repo cho 1 service cụ thể",
        description = """
            Tạo repo cho 1 service trên cả GitHub + GitLab.
            
            Tên service hợp lệ:
            `lisa-realtime-service`, `lisa-lms-service`, `lisa-user-payment-service`,
            `lisa-mobile-app`, `lisa-curriculum-service`, `lisa-api-gateway`
            """
    )
    @ApiResponse(responseCode = "200", description = "Kết quả tạo repo")
    @ApiResponse(responseCode = "500", description = "Tên service không tồn tại")
    @PostMapping("/provision/{serviceName}")
    public ResponseEntity<ProvisionResult> provisionOne(
            @Parameter(description = "Tên service", example = "lisa-lms-service")
            @PathVariable String serviceName,
            @RequestParam(defaultValue = "true") boolean privateRepo,
            @RequestParam(defaultValue = "true") boolean initReadme) {

        return ResponseEntity.ok(
                provisionService.provisionSingleService(serviceName, privateRepo, initReadme));
    }

    @Tag(name = "Provision")
    @Operation(
        summary = "Tạo 1 repo hoàn toàn tùy chỉnh",
        description = "Tạo repo với tên và cấu hình tùy ý, không cần định nghĩa trước."
    )
    @ApiResponse(responseCode = "200", description = "Kết quả tạo repo từng platform")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        content = @Content(
            schema = @Schema(implementation = CreateRepoRequest.class),
            examples = @ExampleObject(value = """
                {
                  "name": "lisa-notification-service",
                  "description": "Push notification service",
                  "privateRepo": true,
                  "initReadme": true,
                  "topics": ["java", "spring-boot", "firebase"],
                  "platform": "BOTH"
                }
                """)
        )
    )
    @PostMapping("/custom")
    public ResponseEntity<List<RepoResult>> createCustom(
            @RequestBody CreateRepoRequest request) {

        return ResponseEntity.ok(provisionService.createSingleRepo(request));
    }

    @Tag(name = "Services")
    @Operation(
        summary = "Xem danh sách services được định nghĩa",
        description = "Trả về toàn bộ services khai báo trong application.yml."
    )
    @GetMapping("/services")
    public ResponseEntity<List<LisaServicesConfig.ServiceDefinition>> listServices() {
        return ResponseEntity.ok(lisaServicesConfig.getServices());
    }
}
