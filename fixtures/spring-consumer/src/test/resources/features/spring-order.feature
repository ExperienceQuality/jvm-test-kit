Feature: Spring-backed company Cucumber utilities

  Scenario: submit a request and assert its response through Spring-managed company utilities
    Given the in-process order API is available
    When I submit an order through the company utility and it is accepted
    Then the created order response includes:
      | id             | status   | customer.id |
      | spring-order-1 | accepted | spring-customer |
    And its customer details contain:
      | id              | name |
      | spring-customer | Rowan |
    And its returned items include:
      | sku   | quantity |
      | SKU-S | 1        |
      | SKU-T | 3        |
