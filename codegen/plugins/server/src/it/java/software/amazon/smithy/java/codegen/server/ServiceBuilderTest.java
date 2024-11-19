/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import smithy.java.codegen.server.test.model.Beer;
import smithy.java.codegen.server.test.model.EchoInput;
import smithy.java.codegen.server.test.model.EchoOutput;
import smithy.java.codegen.server.test.model.EchoPayload;
import smithy.java.codegen.server.test.model.GetBeerInput;
import smithy.java.codegen.server.test.model.GetBeerOutput;
import smithy.java.codegen.server.test.model.GetErrorInput;
import smithy.java.codegen.server.test.model.GetErrorOutput;
import smithy.java.codegen.server.test.service.EchoOperation;
import smithy.java.codegen.server.test.service.GetBeerOperationAsync;
import smithy.java.codegen.server.test.service.GetErrorOperationAsync;
import smithy.java.codegen.server.test.service.TestService;
import software.amazon.smithy.java.core.schema.ModeledApiException;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.RequestContext;
import software.amazon.smithy.java.server.exceptions.InternalServerError;
import software.amazon.smithy.java.server.exceptions.UnknownOperationException;

public class ServiceBuilderTest {

    private static final class EchoImpl implements EchoOperation {
        @Override
        public EchoOutput echo(EchoInput input, RequestContext context) {
            var output = input.value().toBuilder().echoCount(input.value().echoCount() + 1).build();
            return EchoOutput.builder().value(output).build();
        }
    }

    private static final class GetBeerImpl implements GetBeerOperationAsync {
        @Override
        public CompletableFuture<GetBeerOutput> getBeer(GetBeerInput input, RequestContext context) {
            return CompletableFuture.completedFuture(
                GetBeerOutput.builder().value(List.of(Beer.builder().id(input.id()).name("TestBeer").build())).build()
            );
        }
    }

    private static final class GetErrorImpl implements GetErrorOperationAsync {
        @Override
        public CompletableFuture<GetErrorOutput> getError(GetErrorInput input, RequestContext context) {
            Throwable exception;
            try {
                Class<?> clazz = Class.forName(input.exceptionClass());
                if (ModeledApiException.class.isAssignableFrom(clazz)) {
                    Object builderInstance = clazz.getDeclaredMethod("builder").invoke(null);
                    builderInstance = builderInstance.getClass()
                        .getMethod("message")
                        .invoke(builderInstance, input.message());
                    exception = (Throwable) builderInstance.getClass().getMethod("build").invoke(builderInstance);
                } else {
                    exception = (Throwable) clazz.getConstructor().newInstance(input.message());
                }
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException
                | InstantiationException e) {
                exception = new InternalServerError("Exception class not found");
            }
            return CompletableFuture.failedFuture(exception);
        }
    }

    private final TestService service = TestService.builder()
        .addEchoOperation(new EchoImpl())
        .addGetBeerOperation(new GetBeerImpl())
        .addGetErrorOperation(new GetErrorImpl())
        .build();

    @Test
    void testRouting() throws ExecutionException, InterruptedException {
        Operation<GetBeerInput, GetBeerOutput> getBeer = service.getOperation("GetBeer");
        assertThat("GetBeer").isEqualTo(getBeer.name());
        var output = getBeer.asyncFunction().apply(GetBeerInput.builder().id(1).build(), null);
        assertThat(output.get().value())
            .hasSize(1)
            .containsExactly(Beer.builder().id(1).name("TestBeer").build());

        Operation<EchoInput, EchoOutput> echo = service.getOperation("Echo");
        assertThat("Echo").isEqualTo(echo.name());
        var echoOutput = echo.function()
            .apply(EchoInput.builder().value(EchoPayload.builder().string("A").build()).build(), null);
        assertThat(echoOutput.value().echoCount()).isEqualTo(1);
        assertThat(echoOutput.value().string()).isEqualTo("A");
    }

    @Test
    void unknownOperation() {
        assertThatThrownBy(() -> service.getOperation("UnknownOperation"))
            .isInstanceOf(UnknownOperationException.class);
    }
}
