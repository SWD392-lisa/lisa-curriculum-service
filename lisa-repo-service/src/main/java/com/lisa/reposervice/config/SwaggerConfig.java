package com.lisa.reposervice.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LISA Repo Provisioner API")
                        .description("""
                                API để **tự động tạo repo** trên GitHub và GitLab cho tất cả LISA microservices.
                                
                                ## Trước khi dùng
                                Set environment variables:
                                ```
                                GITHUB_TOKEN=ghp_xxx
                                GITHUB_ORG=your-org
                                GITLAB_TOKEN=glpat-xxx
                                GITLAB_NAMESPACE_ID=12345678
                                ```
                                
                                ## Services được tạo repo
                                - `lisa-realtime-service` (Node.js)
                                - `lisa-lms-service` (Java Spring)
                                - `lisa-user-payment-service` (.NET)
                                - `lisa-mobile-app` (Flutter)
                                - `lisa-curriculum-service` (Java Spring)
                                - `lisa-api-gateway` (Spring Cloud)
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("LISA Team – SWD392")
                                .email("lisa-team@fpt.edu.vn")))
                .servers(List.of(
                        new Server().url("http://localhost:8082").description("Local DEV"),
                        new Server().url("http://localhost/").description("Docker (qua Nginx)")
                ))
                .tags(List.of(
                        new Tag().name("Provision").description("Tạo repo tự động"),
                        new Tag().name("Services").description("Xem danh sách services")
                ));
    }
}
