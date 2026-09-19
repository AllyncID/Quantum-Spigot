# Configuration validation checklist

The root loader rejects:

- duplicate YAML keys, aliases, serialized Bukkit objects, null values and scalar sections;
- unknown schema versions (only version `1` is accepted);
- wrong types, non-finite numbers and values outside the documented ranges;
- malformed world keys and invalid redstone or override enums.

A reload is transactional: the candidate is parsed into a new immutable `QuantumConfig`, and only then is the volatile runtime snapshot replaced. The test path also exercises a valid reload, an invalid boolean, and restoration of the valid file on the local Survival server.
