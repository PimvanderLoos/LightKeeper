# LightKeeper documentation

- [IDE test setup](ide-testing.md): prepare a durable local server and run integration tests from an IDE gutter button.
- [Assertion coverage and decisions](assertion-coverage.md): existing assertions, recommended additions, explicit
  exclusions, observation gaps, and implementation priorities across the public API.

Public API changes must update the assertion coverage register in the same PR. Record an explicit decision even when
ordinary AssertJ assertions suffice or no custom assertion should be added. See the register's maintenance steps.
