package org.privote.backend.web3.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;

@Configuration
@EnableConfigurationProperties(Web3jProperties.class)
public class Web3jConfig
{

    @Bean
    public Web3j web3j(Web3jProperties p)
    {
        return Web3j.build(new HttpService(p.getClientAddress()));
    }

    @Bean
    public Credentials relayerCredentials(Web3jProperties p)
    {
        return Credentials.create(p.getPrivateKey());
    }

    @Bean
    public TransactionManager txManager(Web3j web3j, Credentials creds, Web3jProperties p)
    {
        return new RawTransactionManager(web3j, creds, p.getChainId());
    }

    @Bean
    public ContractGasProvider gasProvider()
    {
        return new DefaultGasProvider();
    }
}
