package org.privote.backend.web3.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigInteger;

@Getter
@Setter
@ConfigurationProperties(prefix = "web3j")
public class Web3jProperties
{
    private String clientAddress;
    private long chainId;
    private String privateKey;
    private String electionFactoryAddress;
    private int confirmations = 2;
    private BigInteger startBlock = BigInteger.ZERO;
    private Listener listener = new Listener();

    @Getter
    @Setter
    public static class Listener
    {
        private boolean enabled = true;
        private long pollIntervalMs = 5000;
    }
}
