package org.privote.backend.web3.client;

import org.junit.jupiter.api.Test;
import org.privote.backend.web3.config.Web3jProperties;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.websocket.events.Notification;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElectionFactoryClientTest
{
    @Test
    void resolveFactoryAddressRejectsFactoryAddressWithoutContractCode()
    {
        Web3jProperties props = new Web3jProperties();
        props.setElectionFactoryAddress("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266");
        props.setChainId(1L);

        ElectionFactoryClient client = new ElectionFactoryClient(
                web3jStub(address -> "0x"),
                null,
                null,
                props
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                client::resolveFactoryAddress
        );

        assertEquals(
                "Configured electionFactoryAddress has no contract code: 0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266",
                ex.getMessage()
        );
    }

    @Test
    void resolveFactoryAddressFallsBackToLocalHardhatFactoryWhenConfiguredAddressHasNoCode()
    {
        Web3jProperties props = new Web3jProperties();
        props.setClientAddress("http://127.0.0.1:8545");
        props.setChainId(ElectionFactoryClient.LOCAL_HARDHAT_CHAIN_ID);
        props.setElectionFactoryAddress("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266");

        ElectionFactoryClient client = new ElectionFactoryClient(
                web3jStub(address -> {
                    if (ElectionFactoryClient.LOCAL_HARDHAT_FACTORY_ADDRESS.equalsIgnoreCase(address)) {
                        return "0x6000";
                    }
                    return "0x";
                }),
                null,
                null,
                props
        );

        assertEquals(
                ElectionFactoryClient.LOCAL_HARDHAT_FACTORY_ADDRESS,
                client.resolveFactoryAddress()
        );
    }

    @Test
    void resolveFactoryAddressFallsBackToLocalHardhatFactoryWhenConfigIsMissing()
    {
        Web3jProperties props = new Web3jProperties();
        props.setClientAddress("http://localhost:8545");
        props.setChainId(ElectionFactoryClient.LOCAL_HARDHAT_CHAIN_ID);

        ElectionFactoryClient client = new ElectionFactoryClient(
                web3jStub(address -> {
                    if (ElectionFactoryClient.LOCAL_HARDHAT_FACTORY_ADDRESS.equalsIgnoreCase(address)) {
                        return "0x6000";
                    }
                    return "0x";
                }),
                null,
                null,
                props
        );

        assertEquals(
                ElectionFactoryClient.LOCAL_HARDHAT_FACTORY_ADDRESS,
                client.resolveFactoryAddress()
        );
    }

    private static Web3j web3jStub(java.util.function.Function<String, String> codeByAddress)
    {
        Web3jService service = new Web3jService()
        {
            @Override
            public <T extends Response> T send(Request request, Class<T> responseType)
            {
                String address = request.getParams().getFirst().toString().toLowerCase();
                EthGetCode response = new EthGetCode();
                response.setResult(codeByAddress.apply(address));
                return responseType.cast(response);
            }

            @Override
            public <T extends Response> CompletableFuture<T> sendAsync(Request request, Class<T> responseType)
            {
                try
                {
                    return CompletableFuture.completedFuture(send(request, responseType));
                }
                catch (Exception ex)
                {
                    return CompletableFuture.failedFuture(ex);
                }
            }

            @Override
            public org.web3j.protocol.core.BatchResponse sendBatch(org.web3j.protocol.core.BatchRequest batchRequest) throws IOException
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<org.web3j.protocol.core.BatchResponse> sendBatchAsync(org.web3j.protocol.core.BatchRequest batchRequest)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Notification<?>> io.reactivex.Flowable<T> subscribe(Request request, String unsubscribeMethod, Class<T> responseType)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close()
            {
            }
        };

        return (Web3j) Proxy.newProxyInstance(
                Web3j.class.getClassLoader(),
                new Class[]{Web3j.class},
                (proxy, method, args) -> switch (method.getName())
                {
                    case "ethGetCode" -> new Request<>(
                            "eth_getCode",
                            List.of(args[0], args[1]),
                            service,
                            EthGetCode.class
                    );
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "Web3jStub";
                    default -> throw new UnsupportedOperationException("Unexpected Web3j method: " + method.getName());
                }
        );
    }
}
