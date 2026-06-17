package com.gagroup.ca.confirmations.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(info = @Info(
        title = "Corporate Actions Confirmations API",
        version = "v1",
        description = "Read API for materialised ISO 20022 corporate action settlement confirmations"))
@SpringBootApplication
public class CaConfirmationsApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CaConfirmationsApiApplication.class, args);
    }
}
