## Examples: Closure-Driven Code Generation

This example drives code generation from a shape closure declared in the model instead of from a
service shape. A shape closure is a named set of shapes that does not have to be rooted in a service,
which makes it possible to generate types for shapes no operation refers to. Asynchronous events that
a service publishes are the motivating case.

The model describes a bird-watching club. `com.example.audubon#BirdWatcher` manages a
`SightingResource`, and publishes `SightingReported` and `SightingWithdrawn` events so subscribers
hear about changes without polling. Neither event appears in any operation, so neither is in the
service closure. Both are tagged `event`, and `model/events.smithy` declares a closure that selects
them by that tag:

```smithy
metadata shapeClosures = [
    {
        id: "com.example.audubon#events"
        includeBySelector: "structure[trait|tags|(values) = event]"
    }
]
```

Four subprojects build from that one model, each showing a different way to configure the plugin:

| Subproject | Modes | Closure | What it generates |
|---|---|---|---|
| `types` | `["types"]` | `com.example.audubon#events` | Only the event types. No service is involved, so no `service` setting is needed. |
| `server` | `["server", "types"]` | `com.example.audubon#all` | The service as a server, plus the event types it publishes. |
| `client` | `["client"]` | none | Only the service client. |
| `consumer` | none | none | Nothing. It depends on `types` for the event classes. |

### types

The shared package, and the reason for the feature. It generates the two event structures and the
shapes they reference, and nothing else. Publishing this as an artifact is what lets a producer and
its subscribers agree on a payload without either one regenerating it.

### server

Combined mode, which generates a service and standalone types together. The service is what publishes
the events, so it needs those types alongside its own.

Combined mode requires the primary service to be a member of the closure it generates, so this
subproject drives generation from `com.example.audubon#all`, a closure over the whole namespace.
Pointing it at the events-only closure fails with a message saying the service is not part of it.

### client

A plain client build, driven by the service with no closure at all. The `closure` setting requires
`types` mode, so leaving both out is what makes this an ordinary client. A client calls the service
and has no reason to know about the events, so it neither generates them nor depends on them.

### consumer

A subscriber, which runs no code generator. It depends on `types` and decodes events with the same
classes the service used to encode them.

### Usage

To use this example as a template, run the following command with the
[Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/index.html):

```console
smithy init -t closure-types --url git@github.com:smithy-lang/smithy-java.git
```

Then build it and run the tests:

```console
cd closure-types
gradle build
```

Each subproject's tests show what its configuration produces.
