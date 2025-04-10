$version: "2"

namespace software.amazon.smithy.modelbundle.api

structure Bundle {
    @required
    configType: String

    @required
    serviceName: String

    @required
    config: Document
}
