Feature: Spring-backed company Cucumber utilities

  Scenario: submit a request through the Spring-managed company API utility
    Given the in-process order API is available
    When I submit an order through the company utility
    Then the API receives the order and the company scenario context is active
