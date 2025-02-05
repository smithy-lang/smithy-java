## logging
> [!WARNING]
> This module is intended for internal components of the Smithy Java framework
> and should not be depended on by projects outside of this repository.

This package provides a common logging facade for internal 
Smithy-Java logging. This logging facade supports the following 
logger implementations: 
- JDK System Logger (default)
- Log4j2 Logger
- Slf4j2 Logger
