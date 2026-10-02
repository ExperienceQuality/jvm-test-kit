Feature: JSON table composition

  Scenario: submit a nested order body
    When I submit this order:
      | customer.id | customer.profile.vip | items[0].sku | items[0].quantity | metadata.source |
      | cust-123    | true                  | SKU-1        | 2                 | clean-consumer  |
      | cust-456    | false                 | SKU-2        | 1                 | ui              |
    Then the API receives the composed order
