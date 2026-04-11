# OpenAPI Documentation Design

**Date:** 2026-04-08  
**Branch:** feat/storefront-support  
**Status:** Approved

## Overview

Add OpenAPI 3 documentation to the Garden Spring Boot API using springdoc-openapi. The primary goals are:

1. Interactive Swagger UI for developers to explore and test endpoints
2. Machine-readable OpenAPI spec (`/v3/api-docs`) for client code generation

The UI and spec are disabled in production and enabled in all other environments.

## Dependency

Add `springdoc-openapi-starter-webmvc-ui` to `pom.xml`. This is the correct artifact for Spring MVC projects. The version must be compatible with Spring Boot 4.x — confirm the latest springdoc release that targets Spring Boot 4 at implementation time.

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version><!-- confirm Spring Boot 4.x-compatible version --></version>
</dependency>
```

## OpenAPI Configuration Bean

A new `OpenApiConfig` class in `src/main/java/io/k2dv/garden/config/` defines:

- **API metadata**: title (`Garden API`), version (`0.0.1`), description
- **JWT security scheme**: named `bearerAuth`, HTTP Bearer format, JWT scheme
- **Global security requirement**: applies `bearerAuth` to all operations by default

Controllers or methods with fully public access (storefront browsing, auth login/register) opt out explicitly with `@SecurityRequirements({})` at the method level.

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Garden API")
                .version("0.0.1")
                .description("Garden e-commerce platform API"))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .name("bearerAuth")
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}
```

## Security Config Permit List

Add the following paths to the existing permit list in `SecurityConfig` so unauthenticated users can access the UI and spec in non-prod environments:

- `/swagger-ui/**`
- `/swagger-ui.html`
- `/v3/api-docs/**`

These entries are harmless in production since springdoc won't register those routes when disabled.

## Profile-Based Disabling

In the production profile configuration (e.g., `application-prod.properties`):

```properties
springdoc.api-docs.enabled=false
springdoc.swagger-ui.enabled=false
```

The Swagger UI and spec endpoint are completely unavailable in production. All other environments (local, staging) have them enabled by default without any additional config.

## Controller Tagging

Add `@Tag(name = "...", description = "...")` to each controller class. No `@Operation` annotations on individual methods unless the purpose is non-obvious from the method signature and path — keeping annotation noise low.

| Tag | Controllers |
|---|---|
| Auth | `AuthController` |
| Account | `AccountController` |
| Cart | `CartController` |
| Products | `AdminProductController`, `StorefrontProductController` |
| Collections | `AdminCollectionController`, `StorefrontCollectionController` |
| Inventory | `AdminInventoryController`, `AdminLocationController` |
| Orders | `AdminOrderController` |
| Content | `AdminBlogController`, `AdminPageController`, `StorefrontContentController` |
| Blobs | `BlobController` |
| Admin: Users | `AdminUserController` |
| Admin: IAM | `AdminIamController` |
| Payments | `CheckoutController`, `WebhookController` |

## What Is Not In Scope

- `@Operation` annotations on every method (only where non-obvious)
- `@Schema` annotations on every DTO field (springdoc introspects Jackson/Lombok automatically)
- API versioning or multiple API groups
- Custom Swagger UI theming
