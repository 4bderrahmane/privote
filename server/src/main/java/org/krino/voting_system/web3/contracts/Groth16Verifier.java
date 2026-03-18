package org.krino.voting_system.web3.contracts;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/LFDT-web3j/web3j/tree/main/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 1.7.0.
 */
@SuppressWarnings("rawtypes")
public class Groth16Verifier extends Contract {
    public static final String BINARY = "0x6080604052348015600e575f5ffd5b506106f98061001c5f395ff3fe608060405234801561000f575f5ffd5b5060043610610029575f3560e01c80635fe8c13b1461002d575b5f5ffd5b6100476004803603810190610042919061062a565b61005d565b60405161005491906106aa565b60405180910390f35b5f61056c565b7f30644e72e131a029b85045b68181585d2833e84879b9709143e1f593f00000018110610092575f5f5260205ff35b50565b5f60405183815284602082015285604082015260408160608360076107d05a03fa9150816100c5575f5f5260205ff35b825160408201526020830151606082015260408360808360066107d05a03fa9150816100f3575f5f5260205ff35b505050505050565b5f608086015f87017f28bd9bf151b98ee5ee2b77eccf26d3a423dd0c540d6b93d243183b40adb4a14e81527f2ea1547b60306bf034f27cbf6e8362ea7fea873ac669d8b0eeca2a81f01ad017602082015261019b5f8801357f02c7f1c64aa26c7e2f5f43e591bcf995b4d3637814abe42a8287cd7d91ac9aa87f2e5ae182499a52e10956e9a7afb89dc05597593c86007ad6e19e505e769d2c3a84610095565b6101eb60208801357f04a8d9d63bf8ae254fcbb0cedbb8ec88e39e9f6c9ba64bc0a09eeaf97c5752187f0cbbaaf89a73eb3126443c0595c83208975a552e331ae75457145b5502a02d0d84610095565b61023b60408801357f2352350a3cba8a0d43a4fd1de7ed0a2f5268f996ed6cbc514ee209810ada0c717f0fd147dae0b10a4b283e36466c3fc3f46ea90fae4bcb5d549770f9a853777eb784610095565b61028b60608801357f2e5c469f07fd2a8c7e03ea2a3b147b20565c9d31716bba122947617fd23ed8e77f263bf514f898ba8b6034e93caee06a75fe565d024568dc6d7def0f572ff66f0384610095565b833582527f30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd4760208501357f30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd4703066020830152843560408301526020850135606083015260408501356080830152606085013560a08301527f2d4d9aa7e302d9df41749d5507949d05dbea33fbb16c643b22f599a2be6df2e260c08301527f14bedd503c37ceb061d8ec60209fe345ce89830a19230301f076caff004d192660e08301527f0967032fcbf776d1afc985f88877f182d38480a653f2decaa9794cbc3bf3060c6101008301527f0e187847ad4c798374d0d6732bf501847dd68bc0e071241e0213bc7fc13db7ab6101208301527f304cfbd1e08a704a99f5e847d93f8c3caafddec46b7a0d379da69a4d112346a76101408301527f1739c1b1a457a8c7313123d24d2f9192f896b7c63eea05a9d57f06547ad0cec86101608301525f88015161018083015260205f018801516101a08301527f198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c26101c08301527f1800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed6101e08301527f090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b6102008301527f12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa610220830152853561024083015260208601356102608301527f0707c93b225271b42a64a9cefabf53370f0e7c101f0c55139a30c86e65df082a6102808301527f207560a2d88849f5b1bac48b1f6568b015005ef11676b7e2311cb6f7ebf4e7446102a08301527f10aeba6fbfbfe6c754142a6ca87ec0d077054c86c97b1b1118fd426207e3f9b66102c08301527e59c99f345783f40bb5698bd79024e6fb3e9b4633c4d410ed0dd6f64f233d726102e08301526020826103008460086107d05a03fa82518116935050505095945050505050565b60405161038081016040526105835f840135610063565b6105906020840135610063565b61059d6040840135610063565b6105aa6060840135610063565b6105b7818486888a6100fb565b805f5260205ff35b5f5ffd5b5f5ffd5b5f819050826020600202820111156105e2576105e16105c3565b5b92915050565b5f81905082604060020282011115610603576106026105c3565b5b92915050565b5f81905082602060040282011115610624576106236105c3565b5b92915050565b5f5f5f5f6101808587031215610643576106426105bf565b5b5f610650878288016105c7565b9450506040610661878288016105e8565b93505060c0610672878288016105c7565b92505061010061068487828801610609565b91505092959194509250565b5f8115159050919050565b6106a481610690565b82525050565b5f6020820190506106bd5f83018461069b565b9291505056fea2646970667358221220a71040c047f84db3a71d8318aebd70519c378f9be5604fa4c258ebda7fb4362e64736f6c634300081c0033\n";

    private static String librariesLinkedBinary;

    public static final String FUNC_VERIFYPROOF = "verifyProof";

    @Deprecated
    protected Groth16Verifier(String contractAddress, Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected Groth16Verifier(String contractAddress, Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected Groth16Verifier(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected Groth16Verifier(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public RemoteFunctionCall<Boolean> verifyProof(List<BigInteger> _pA, List<List<BigInteger>> _pB,
            List<BigInteger> _pC, List<BigInteger> _pubSignals) {
        final Function function = new Function(FUNC_VERIFYPROOF, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.StaticArray2<org.web3j.abi.datatypes.generated.Uint256>(
                        org.web3j.abi.datatypes.generated.Uint256.class,
                        org.web3j.abi.Utils.typeMap(_pA, org.web3j.abi.datatypes.generated.Uint256.class)), 
                new org.web3j.abi.datatypes.generated.StaticArray2<org.web3j.abi.datatypes.generated.StaticArray2>(
                        org.web3j.abi.datatypes.generated.StaticArray2.class,
                        org.web3j.abi.Utils.typeMap(_pB, org.web3j.abi.datatypes.generated.StaticArray2.class,
                org.web3j.abi.datatypes.generated.Uint256.class)), 
                new org.web3j.abi.datatypes.generated.StaticArray2<org.web3j.abi.datatypes.generated.Uint256>(
                        org.web3j.abi.datatypes.generated.Uint256.class,
                        org.web3j.abi.Utils.typeMap(_pC, org.web3j.abi.datatypes.generated.Uint256.class)), 
                new org.web3j.abi.datatypes.generated.StaticArray4<org.web3j.abi.datatypes.generated.Uint256>(
                        org.web3j.abi.datatypes.generated.Uint256.class,
                        org.web3j.abi.Utils.typeMap(_pubSignals, org.web3j.abi.datatypes.generated.Uint256.class))), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    @Deprecated
    public static Groth16Verifier load(String contractAddress, Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        return new Groth16Verifier(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static Groth16Verifier load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new Groth16Verifier(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static Groth16Verifier load(String contractAddress, Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        return new Groth16Verifier(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static Groth16Verifier load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new Groth16Verifier(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<Groth16Verifier> deploy(Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        return deployRemoteCall(Groth16Verifier.class, web3j, credentials, contractGasProvider, getDeploymentBinary(), "");
    }

    @Deprecated
    public static RemoteCall<Groth16Verifier> deploy(Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(Groth16Verifier.class, web3j, credentials, gasPrice, gasLimit, getDeploymentBinary(), "");
    }

    public static RemoteCall<Groth16Verifier> deploy(Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(Groth16Verifier.class, web3j, transactionManager, contractGasProvider, getDeploymentBinary(), "");
    }

    @Deprecated
    public static RemoteCall<Groth16Verifier> deploy(Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(Groth16Verifier.class, web3j, transactionManager, gasPrice, gasLimit, getDeploymentBinary(), "");
    }

    public static void linkLibraries(List<Contract.LinkReference> references) {
        librariesLinkedBinary = linkBinaryWithReferences(BINARY, references);
    }

    private static String getDeploymentBinary() {
        if (librariesLinkedBinary != null) {
            return librariesLinkedBinary;
        } else {
            return BINARY;
        }
    }
}
