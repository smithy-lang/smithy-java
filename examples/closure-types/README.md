## Examples: Closure-Driven Combined Generation

This example demonstrates driving code generation from a shape closure defined in the model rather
than from a service shape alone. It uses "combined mode": a service is generated as a server, and
the data shapes in a modeled shape closure are generated alongside it.

The bird-watching service (`smithy.example.birds#iBird`) is generated as an RPC v2 CBOR server. The
`smithy.example.birds#fullService` closure includes the whole service namespace, so the event types
(such as `VerifiedSighting`) are generated alongside the server even though some are not reachable
from the service's operations.

The `closure` setting in `smithy-build.json` references the `shapeClosures` entry authored in the
model, so the closure definition travels with the model rather than living in build configuration.

### Usage

To use this example as a template, run the following command with the
[Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/index.html):

```console
smithy init -t closure-types --url git@github.com:smithy-lang/smithy-java.git
```
