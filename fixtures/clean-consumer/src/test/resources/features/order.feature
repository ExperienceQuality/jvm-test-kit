Feature: JSON table composition

  Scenario: submit a nested order and inspect the API response
    When I submit this order and the service accepts it:
      | customer.id | customer.profile.vip | items[0].sku | items[0].quantity | metadata.source |
      | cust-123    | true                  | SKU-1        | 2                 | clean-consumer  |
      | cust-456    | false                 | SKU-2        | 1                 | ui              |
    Then the created order response includes:
      | id         | status   | customer.id |
      | order-123  | accepted | cust-123    |
    And its customer details contain:
      | id       | name |
      | cust-123 | Mina |
    And its returned items include:
      | sku   | quantity |
      | SKU-1 | 2        |
      | SKU-2 | 1        |
