package com.lisa.curriculum.config;

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
                        .title("LISA Curriculum Service API")
                        .description("""
                                API để số hóa và quản lý **100 levels** ngôn ngữ của LISA app.
                                
                                ## Tính năng
                                - **Import** 8 file Word (.docx) → Database (English, Chinese, Japanese)
                                - **Query** levels theo ngôn ngữ, stage, level number
                                - **Statistics** số lượng levels đã import
                                
                                ## Cách dùng Swagger này
                                1. Import file Word trước qua `POST /api/curriculum/import`
                                2. Dùng `GET /api/curriculum/stats` để xem đã import được bao nhiêu
                                3. Query data qua các endpoint GET
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("LISA Team – SWD392")
                                .email("lisa-team@fpt.edu.vn")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local DEV"),
                        new Server().url("http://localhost/").description("Docker (qua Nginx)")
                ))
                .tags(List.of(
                        new Tag().name("Import").description("Import file Word vào database"),
                        new Tag().name("Levels").description("Query levels curriculum"),
                        new Tag().name("Stats").description("Thống kê dữ liệu")
                ));
    }
}
