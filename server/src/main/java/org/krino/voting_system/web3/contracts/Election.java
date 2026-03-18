package org.krino.voting_system.web3.contracts;

import io.reactivex.Flowable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Array;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.CustomError;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
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
public class Election extends Contract {
    public static final String BINARY = "0x610120604052348015610010575f5ffd5b5060405161265338038061265383398181016040528101906100329190610469565b5f73ffffffffffffffffffffffffffffffffffffffff168573ffffffffffffffffffffffffffffffffffffffff16148061008257505f8573ffffffffffffffffffffffffffffffffffffffff163b145b156100b9576040517ff87aa2e900000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b5f73ffffffffffffffffffffffffffffffffffffffff168473ffffffffffffffffffffffffffffffffffffffff160361011e576040517f031c021300000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b5f8303610157576040517f59ad6e9200000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b428211610190576040517fe412609d00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b5f5f1b81036101cb576040517ff764a84200000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b8473ffffffffffffffffffffffffffffffffffffffff1660808173ffffffffffffffffffffffffffffffffffffffff16815250508373ffffffffffffffffffffffffffffffffffffffff1660a08173ffffffffffffffffffffffffffffffffffffffff16815250508260c081815250508160e081815250508061010081815250505f60035f6101000a81548160ff021916908360028111156102705761026f6104e0565b5b0217905550610285838561028f60201b60201c565b505050505061050d565b8060015f8481526020019081526020015f205f6101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff160217905550817ff0adfb94eab6daf835deb69c5738fe636150c3dfd08094a76f39b963dc8cb05a60405160405180910390a28073ffffffffffffffffffffffffffffffffffffffff165f73ffffffffffffffffffffffffffffffffffffffff16837f0ba83579a0e79193ef649b9f5a8759d35af086ba62a3e207b52e4a8ae30d49e360405160405180910390a45050565b5f5ffd5b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f6103978261036e565b9050919050565b5f6103a88261038d565b9050919050565b6103b88161039e565b81146103c2575f5ffd5b50565b5f815190506103d3816103af565b92915050565b6103e28161038d565b81146103ec575f5ffd5b50565b5f815190506103fd816103d9565b92915050565b5f819050919050565b61041581610403565b811461041f575f5ffd5b50565b5f815190506104308161040c565b92915050565b5f819050919050565b61044881610436565b8114610452575f5ffd5b50565b5f815190506104638161043f565b92915050565b5f5f5f5f5f60a086880312156104825761048161036a565b5b5f61048f888289016103c5565b95505060206104a0888289016103ef565b94505060406104b188828901610422565b93505060606104c288828901610422565b92505060806104d388828901610455565b9150509295509295909350565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52602160045260245ffd5b60805160a05160c05160e051610100516120a76105ac5f395f610d2c01525f818161057a015281816106be01528181610af801528181610c1201528181610cd701526110eb01525f81816107eb015281816109a001528181610d500152818161114901526111aa01525f818161043e0152818161048901528181610b2201528181610e370152610f0b01525f8181610463015261082101526120a75ff3fe608060405234801561000f575f5ffd5b5060043610610135575f3560e01c806370bb640f116100b6578063c00300321161007a578063c00300321461033d578063c19d93fb1461035b578063c6c0572d14610379578063c9b26db4146103a9578063d8f7a0bb146103c5578063dabc4d51146103e157610135565b806370bb640f1461027157806378e979251461028f5780637ee35a0c146102ad57806390509d44146102dd578063a9961c941461030d57610135565b80633197cbb6116100fd5780633197cbb6146101dd578063466712b5146101fb57806362d73eb8146102195780636389e107146102235780636b902cf51461025357610135565b806306dd8485146101395780630a009097146101695780632b7ac3f3146101875780632f23aaa7146101a55780633171b624146101c1575b5f5ffd5b610153600480360381019061014e9190611589565b610411565b60405161016091906115d6565b60405180910390f35b61017161043c565b60405161017e919061162e565b60405180910390f35b61018f610460565b60405161019c919061162e565b60405180910390f35b6101bf60048036038101906101ba91906116a8565b610487565b005b6101db60048036038101906101d69190611714565b610650565b005b6101e5610af6565b6040516101f291906115d6565b60405180910390f35b610203610b1a565b60405161021091906115d6565b60405180910390f35b610221610b20565b005b61023d60048036038101906102389190611786565b610d0e565b60405161024a91906115d6565b60405180910390f35b61025b610d2a565b60405161026891906117c9565b60405180910390f35b610279610d4e565b60405161028691906115d6565b60405180910390f35b610297610d72565b6040516102a491906115d6565b60405180910390f35b6102c760048036038101906102c29190611786565b610d78565b6040516102d491906115d6565b60405180910390f35b6102f760048036038101906102f29190611589565b610d93565b60405161030491906117fc565b60405180910390f35b61032760048036038101906103229190611786565b610dbe565b604051610334919061162e565b60405180910390f35b610345610df7565b60405161035291906115d6565b60405180910390f35b610363610dfd565b6040516103709190611888565b60405180910390f35b610393600480360381019061038e9190611786565b610e0f565b6040516103a091906117fc565b60405180910390f35b6103c360048036038101906103be91906118f6565b610e35565b005b6103df60048036038101906103da9190611786565b610f09565b005b6103fb60048036038101906103f69190611786565b610fa2565b60405161040891906115d6565b60405180910390f35b5f610434825f5f8681526020019081526020015f20610fc290919063ffffffff16565b905092915050565b7f000000000000000000000000000000000000000000000000000000000000000081565b5f7f0000000000000000000000000000000000000000000000000000000000000000905090565b7f000000000000000000000000000000000000000000000000000000000000000073ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff161461050c576040517fe5cb7e3100000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b600160028111156105205761051f611815565b5b60035f9054906101000a900460ff16600281111561054157610540611815565b5b14610578576040517fbe7549f100000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b7f00000000000000000000000000000000000000000000000000000000000000004210156105d2576040517f84a5861900000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b600260035f6101000a81548160ff021916908360028111156105f7576105f6611815565b5b02179055503373ffffffffffffffffffffffffffffffffffffffff167fe1c2e147700bc44992019e412741318d839b68884b3e58d11352ce26c82746f4838360405161064492919061199b565b60405180910390a25050565b6001600281111561066457610663611815565b5b60035f9054906101000a900460ff16600281111561068557610684611815565b5b146106bc576040517fbe7549f100000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b7f00000000000000000000000000000000000000000000000000000000000000004210610715576040517f66f2636900000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b5f8484905003610751576040517f721348f000000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b610400848490501115610790576040517f8b65540d00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b60065f8381526020019081526020015f205f9054906101000a900460ff16156107e5576040517fd812f50000000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b5f61080f7f0000000000000000000000000000000000000000000000000000000000000000610fa2565b90505f61081c8686611037565b90505f7f000000000000000000000000000000000000000000000000000000000000000073ffffffffffffffffffffffffffffffffffffffff16635fe8c13b6040518060400160405280875f60088110610879576108786119bd565b5b6020020135815260200187600160088110610897576108966119bd565b5b6020020135815250604051806040016040528060405180604001604052808a6002600881106108c9576108c86119bd565b5b602002013581526020018a6003600881106108e7576108e66119bd565b5b6020020135815250815260200160405180604001604052808a600460088110610913576109126119bd565b5b602002013581526020018a600560088110610931576109306119bd565b5b602002013581525081525060405180604001604052808960066008811061095b5761095a6119bd565b5b6020020135815260200189600760088110610979576109786119bd565b5b602002013581525060405180608001604052808981526020018b81526020018881526020017f00000000000000000000000000000000000000000000000000000000000000008152506040518563ffffffff1660e01b81526004016109e19493929190611c04565b602060405180830381865afa1580156109fc573d5f5f3e3d5ffd5b505050506040513d601f19601f82011682018060405250810190610a209190611c73565b905080610a59576040517f329650dd00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b600160065f8781526020019081526020015f205f6101000a81548160ff021916908315150217905550600160055f828254610a949190611ccb565b92505081905550848787604051610aac929190611d2c565b60405180910390207fc3bb65eca99ab520c6cc5c912598d861648c1faa04a0f37acd287c507810bc9c8989604051610ae592919061199b565b60405180910390a350505050505050565b7f000000000000000000000000000000000000000000000000000000000000000081565b61040081565b7f000000000000000000000000000000000000000000000000000000000000000073ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff1614610ba5576040517fe5cb7e3100000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b5f6002811115610bb857610bb7611815565b5b60035f9054906101000a900460ff166002811115610bd957610bd8611815565b5b14610c10576040517f5bc3857d00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b7f00000000000000000000000000000000000000000000000000000000000000004210610c69576040517f97a2554c00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b42600481905550600160035f6101000a81548160ff02191690836002811115610c9557610c94611815565b5b02179055503373ffffffffffffffffffffffffffffffffffffffff167f711f4f5cdda41420ee1ea628dc5ae0604c1b7809a0dd538249239072a55a33a96004547f0000000000000000000000000000000000000000000000000000000000000000604051610d04929190611d44565b60405180910390a2565b5f5f5f8381526020019081526020015f20600101549050919050565b7f000000000000000000000000000000000000000000000000000000000000000081565b7f000000000000000000000000000000000000000000000000000000000000000081565b60045481565b5f5f5f8381526020019081526020015f205f01549050919050565b5f610db6825f5f8681526020019081526020015f2061105e90919063ffffffff16565b905092915050565b5f60015f8381526020019081526020015f205f9054906101000a900473ffffffffffffffffffffffffffffffffffffffff169050919050565b60055481565b60035f9054906101000a900460ff1681565b5f60065f8381526020019081526020015f205f9054906101000a900460ff169050919050565b7f000000000000000000000000000000000000000000000000000000000000000073ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff1614610eba576040517fe5cb7e3100000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b610ec261107e565b5f8282905090505f5f90505b81811015610f0357610ef8848483818110610eec57610eeb6119bd565b5b90506020020135611144565b806001019050610ece565b50505050565b7f000000000000000000000000000000000000000000000000000000000000000073ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff1614610f8e576040517fe5cb7e3100000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b610f9661107e565b610f9f81611144565b50565b5f610fbb5f5f8481526020019081526020015f206111d3565b9050919050565b5f5f836003015f8481526020019081526020015f20540361100f576040517f7204756c00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b6001836003015f8481526020019081526020015f205461102f9190611d6b565b905092915050565b5f6008838360405161104a929190611d2c565b60405180910390205f1c901c905092915050565b5f5f836003015f8481526020019081526020015f20541415905092915050565b5f600281111561109157611090611815565b5b60035f9054906101000a900460ff1660028111156110b2576110b1611815565b5b146110e9576040517f5bc3857d00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b7f00000000000000000000000000000000000000000000000000000000000000004210611142576040517f97a2554c00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b565b61116e7f000000000000000000000000000000000000000000000000000000000000000082610d93565b156111a5576040517fb9a41c8200000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b6111cf7f0000000000000000000000000000000000000000000000000000000000000000826111f3565b5050565b5f816002015f836001015481526020019081526020015f20549050919050565b5f823373ffffffffffffffffffffffffffffffffffffffff1660015f8381526020019081526020015f205f9054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff161461128a576040517fbb9bf27800000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b5f61129485610d78565b90506112b8845f5f8881526020019081526020015f206112fe90919063ffffffff16565b9250847f19239b3f93cd10558aaf11423af70c77763bf54f52bcc75bfa74d4d13548cde98286866040516112ee93929190611d9e565b60405180910390a2505092915050565b5f7f30644e72e131a029b85045b68181585d2833e84879b9709143e1f593f00000018210611358576040517fc380a82e00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b5f8203611391576040517f29691be200000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b61139b838361105e565b156113d2576040517f258a195a00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b5f835f015490505f846001015490506001826113ee9190611ccb565b8160026113fb9190611f02565b101561140e578061140b90611f4c565b90505b8085600101819055505f8490505f5f90505b828110156114fd576001808286901c16036114d95773__$078c82ddf6c95d34ea184ef1dd6130d136$__63561558fe60405180604001604052808a6002015f8681526020019081526020015f20548152602001858152506040518263ffffffff1660e01b81526004016114939190612019565b602060405180830381865af41580156114ae573d5f5f3e3d5ffd5b505050506040513d601f19601f820116820180604052508101906114d29190612046565b91506114f2565b81876002015f8381526020019081526020015f20819055505b806001019050611420565b508261150890611f4c565b925082865f018190555080866002015f8481526020019081526020015f208190555082866003015f8781526020019081526020015f208190555080935050505092915050565b5f5ffd5b5f5ffd5b5f819050919050565b61156881611556565b8114611572575f5ffd5b50565b5f813590506115838161155f565b92915050565b5f5f6040838503121561159f5761159e61154e565b5b5f6115ac85828601611575565b92505060206115bd85828601611575565b9150509250929050565b6115d081611556565b82525050565b5f6020820190506115e95f8301846115c7565b92915050565b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f611618826115ef565b9050919050565b6116288161160e565b82525050565b5f6020820190506116415f83018461161f565b92915050565b5f5ffd5b5f5ffd5b5f5ffd5b5f5f83601f84011261166857611667611647565b5b8235905067ffffffffffffffff8111156116855761168461164b565b5b6020830191508360018202830111156116a1576116a061164f565b5b9250929050565b5f5f602083850312156116be576116bd61154e565b5b5f83013567ffffffffffffffff8111156116db576116da611552565b5b6116e785828601611653565b92509250509250929050565b5f8190508260206008028201111561170e5761170d61164f565b5b92915050565b5f5f5f5f610140858703121561172d5761172c61154e565b5b5f85013567ffffffffffffffff81111561174a57611749611552565b5b61175687828801611653565b9450945050602061176987828801611575565b925050604061177a878288016116f3565b91505092959194509250565b5f6020828403121561179b5761179a61154e565b5b5f6117a884828501611575565b91505092915050565b5f819050919050565b6117c3816117b1565b82525050565b5f6020820190506117dc5f8301846117ba565b92915050565b5f8115159050919050565b6117f6816117e2565b82525050565b5f60208201905061180f5f8301846117ed565b92915050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52602160045260245ffd5b6003811061185357611852611815565b5b50565b5f81905061186382611842565b919050565b5f61187282611856565b9050919050565b61188281611868565b82525050565b5f60208201905061189b5f830184611879565b92915050565b5f5f83601f8401126118b6576118b5611647565b5b8235905067ffffffffffffffff8111156118d3576118d261164b565b5b6020830191508360208202830111156118ef576118ee61164f565b5b9250929050565b5f5f6020838503121561190c5761190b61154e565b5b5f83013567ffffffffffffffff81111561192957611928611552565b5b611935858286016118a1565b92509250509250929050565b5f82825260208201905092915050565b828183375f83830152505050565b5f601f19601f8301169050919050565b5f61197a8385611941565b9350611987838584611951565b6119908361195f565b840190509392505050565b5f6020820190508181035f8301526119b481848661196f565b90509392505050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52603260045260245ffd5b5f60029050919050565b5f81905092915050565b5f819050919050565b611a1081611556565b82525050565b5f611a218383611a07565b60208301905092915050565b5f602082019050919050565b611a42816119ea565b611a4c81846119f4565b9250611a57826119fe565b805f5b83811015611a87578151611a6e8782611a16565b9650611a7983611a2d565b925050600181019050611a5a565b505050505050565b5f60029050919050565b5f81905092915050565b5f819050919050565b5f81905092915050565b611abf816119ea565b611ac98184611aac565b9250611ad4826119fe565b805f5b83811015611b04578151611aeb8782611a16565b9650611af683611a2d565b925050600181019050611ad7565b505050505050565b5f611b178383611ab6565b60408301905092915050565b5f602082019050919050565b611b3881611a8f565b611b428184611a99565b9250611b4d82611aa3565b805f5b83811015611b7d578151611b648782611b0c565b9650611b6f83611b23565b925050600181019050611b50565b505050505050565b5f60049050919050565b5f81905092915050565b5f819050919050565b5f602082019050919050565b611bb781611b85565b611bc18184611b8f565b9250611bcc82611b99565b805f5b83811015611bfc578151611be38782611a16565b9650611bee83611ba2565b925050600181019050611bcf565b505050505050565b5f61018082019050611c185f830187611a39565b611c256040830186611b2f565b611c3260c0830185611a39565b611c40610100830184611bae565b95945050505050565b611c52816117e2565b8114611c5c575f5ffd5b50565b5f81519050611c6d81611c49565b92915050565b5f60208284031215611c8857611c8761154e565b5b5f611c9584828501611c5f565b91505092915050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52601160045260245ffd5b5f611cd582611556565b9150611ce083611556565b9250828201905080821115611cf857611cf7611c9e565b5b92915050565b5f81905092915050565b5f611d138385611cfe565b9350611d20838584611951565b82840190509392505050565b5f611d38828486611d08565b91508190509392505050565b5f604082019050611d575f8301856115c7565b611d6460208301846115c7565b9392505050565b5f611d7582611556565b9150611d8083611556565b9250828203905081811115611d9857611d97611c9e565b5b92915050565b5f606082019050611db15f8301866115c7565b611dbe60208301856115c7565b611dcb60408301846115c7565b949350505050565b5f8160011c9050919050565b5f5f8291508390505b6001851115611e2857808604811115611e0457611e03611c9e565b5b6001851615611e135780820291505b8081029050611e2185611dd3565b9450611de8565b94509492505050565b5f82611e405760019050611efb565b81611e4d575f9050611efb565b8160018114611e635760028114611e6d57611e9c565b6001915050611efb565b60ff841115611e7f57611e7e611c9e565b5b8360020a915084821115611e9657611e95611c9e565b5b50611efb565b5060208310610133831016604e8410600b8410161715611ed15782820a905083811115611ecc57611ecb611c9e565b5b611efb565b611ede8484846001611ddf565b92509050818404811115611ef557611ef4611c9e565b5b81810290505b9392505050565b5f611f0c82611556565b9150611f1783611556565b9250611f447fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff8484611e31565b905092915050565b5f611f5682611556565b91507fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff8203611f8857611f87611c9e565b5b600182019050919050565b5f81905092915050565b611fa681611556565b82525050565b5f611fb78383611f9d565b60208301905092915050565b611fcc816119ea565b611fd68184611f93565b9250611fe1826119fe565b805f5b83811015612011578151611ff88782611fac565b965061200383611a2d565b925050600181019050611fe4565b505050505050565b5f60408201905061202c5f830184611fc3565b92915050565b5f815190506120408161155f565b92915050565b5f6020828403121561205b5761205a61154e565b5b5f61206884828501612032565b9150509291505056fea26469706673582212206e3784788c185fbb60046621e6cc9e8c38f0e4c019dafb36212ad562884669f564736f6c634300081c0033\n";

    private static String librariesLinkedBinary;

    public static final String FUNC_MAX_CIPHERTEXT_BYTES = "MAX_CIPHERTEXT_BYTES";

    public static final String FUNC_ADDVOTER = "addVoter";

    public static final String FUNC_ADDVOTERS = "addVoters";

    public static final String FUNC_BALLOTCOUNT = "ballotCount";

    public static final String FUNC_CASTVOTE = "castVote";

    public static final String FUNC_COORDINATOR = "coordinator";

    public static final String FUNC_ENCRYPTIONPUBLICKEY = "encryptionPublicKey";

    public static final String FUNC_ENDELECTION = "endElection";

    public static final String FUNC_ENDTIME = "endTime";

    public static final String FUNC_EXTERNALNULLIFIER = "externalNullifier";

    public static final String FUNC_GETGROUPADMIN = "getGroupAdmin";

    public static final String FUNC_GETMERKLETREEDEPTH = "getMerkleTreeDepth";

    public static final String FUNC_GETMERKLETREEROOT = "getMerkleTreeRoot";

    public static final String FUNC_GETMERKLETREESIZE = "getMerkleTreeSize";

    public static final String FUNC_HASMEMBER = "hasMember";

    public static final String FUNC_INDEXOF = "indexOf";

    public static final String FUNC_ISNULLIFIERUSED = "isNullifierUsed";

    public static final String FUNC_STARTELECTION = "startElection";

    public static final String FUNC_STARTTIME = "startTime";

    public static final String FUNC_STATE = "state";

    public static final String FUNC_VERIFIER = "verifier";

    public static final CustomError ELECTION__CALLERISNOTCOORDINATOR_ERROR = new CustomError("Election__CallerIsNotCoordinator", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__CIPHERTEXTTOOLARGE_ERROR = new CustomError("Election__CiphertextTooLarge", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__ELECTIONHASENDED_ERROR = new CustomError("Election__ElectionHasEnded", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__ELECTIONHASNOTENDEDYET_ERROR = new CustomError("Election__ElectionHasNotEndedYet", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__EMPTYCIPHERTEXT_ERROR = new CustomError("Election__EmptyCiphertext", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__INVALIDCOORDINATOR_ERROR = new CustomError("Election__InvalidCoordinator", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__INVALIDENCRYPTIONPUBLICKEY_ERROR = new CustomError("Election__InvalidEncryptionPublicKey", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__INVALIDENDTIME_ERROR = new CustomError("Election__InvalidEndTime", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__INVALIDEXTERNALNULLIFIER_ERROR = new CustomError("Election__InvalidExternalNullifier", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__INVALIDPROOF_ERROR = new CustomError("Election__InvalidProof", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__INVALIDVERIFIER_ERROR = new CustomError("Election__InvalidVerifier", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__MEMBERALREADYEXISTS_ERROR = new CustomError("Election__MemberAlreadyExists", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__NOTINREGISTRATIONPHASE_ERROR = new CustomError("Election__NotInRegistrationPhase", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__NOTINVOTINGPHASE_ERROR = new CustomError("Election__NotInVotingPhase", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__NULLIFIERALREADYUSED_ERROR = new CustomError("Election__NullifierAlreadyUsed", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError ELECTION__VOTINGWINDOWELAPSED_ERROR = new CustomError("Election__VotingWindowElapsed", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError LEAFALREADYEXISTS_ERROR = new CustomError("LeafAlreadyExists", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError LEAFCANNOTBEZERO_ERROR = new CustomError("LeafCannotBeZero", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError LEAFDOESNOTEXIST_ERROR = new CustomError("LeafDoesNotExist", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError LEAFGREATERTHANSNARKSCALARFIELD_ERROR = new CustomError("LeafGreaterThanSnarkScalarField", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError SEMAPHORE__CALLERISNOTTHEGROUPADMIN_ERROR = new CustomError("Semaphore__CallerIsNotTheGroupAdmin", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError SEMAPHORE__CALLERISNOTTHEPENDINGGROUPADMIN_ERROR = new CustomError("Semaphore__CallerIsNotThePendingGroupAdmin", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final CustomError SEMAPHORE__GROUPDOESNOTEXIST_ERROR = new CustomError("Semaphore__GroupDoesNotExist", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final Event ELECTIONENDED_EVENT = new Event("ElectionEnded", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<DynamicBytes>() {}));
    ;

    public static final Event ELECTIONSTARTED_EVENT = new Event("ElectionStarted", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event GROUPADMINPENDING_EVENT = new Event("GroupAdminPending", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}));
    ;

    public static final Event GROUPADMINUPDATED_EVENT = new Event("GroupAdminUpdated", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}));
    ;

    public static final Event GROUPCREATED_EVENT = new Event("GroupCreated", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>(true) {}));
    ;

    public static final Event MEMBERADDED_EVENT = new Event("MemberAdded", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event MEMBERREMOVED_EVENT = new Event("MemberRemoved", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event MEMBERUPDATED_EVENT = new Event("MemberUpdated", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event MEMBERSADDED_EVENT = new Event("MembersAdded", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>(true) {}, new TypeReference<Uint256>() {}, new TypeReference<DynamicArray<Uint256>>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event VOTEADDED_EVENT = new Event("VoteAdded", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>(true) {}, new TypeReference<Uint256>(true) {}, new TypeReference<DynamicBytes>() {}));
    ;

    @Deprecated
    protected Election(String contractAddress, Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected Election(String contractAddress, Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected Election(String contractAddress, Web3j web3j, TransactionManager transactionManager,
            BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected Election(String contractAddress, Web3j web3j, TransactionManager transactionManager,
            ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static List<ElectionEndedEventResponse> getElectionEndedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(ELECTIONENDED_EVENT, transactionReceipt);
        ArrayList<ElectionEndedEventResponse> responses = new ArrayList<ElectionEndedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            ElectionEndedEventResponse typedResponse = new ElectionEndedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.coordinator = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.decryptionMaterial = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static ElectionEndedEventResponse getElectionEndedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(ELECTIONENDED_EVENT, log);
        ElectionEndedEventResponse typedResponse = new ElectionEndedEventResponse();
        typedResponse.log = log;
        typedResponse.coordinator = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.decryptionMaterial = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<ElectionEndedEventResponse> electionEndedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getElectionEndedEventFromLog(log));
    }

    public Flowable<ElectionEndedEventResponse> electionEndedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ELECTIONENDED_EVENT));
        return electionEndedEventFlowable(filter);
    }

    public static List<ElectionStartedEventResponse> getElectionStartedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(ELECTIONSTARTED_EVENT, transactionReceipt);
        ArrayList<ElectionStartedEventResponse> responses = new ArrayList<ElectionStartedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            ElectionStartedEventResponse typedResponse = new ElectionStartedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.coordinator = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.startTime = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.endTime = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static ElectionStartedEventResponse getElectionStartedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(ELECTIONSTARTED_EVENT, log);
        ElectionStartedEventResponse typedResponse = new ElectionStartedEventResponse();
        typedResponse.log = log;
        typedResponse.coordinator = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.startTime = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.endTime = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        return typedResponse;
    }

    public Flowable<ElectionStartedEventResponse> electionStartedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getElectionStartedEventFromLog(log));
    }

    public Flowable<ElectionStartedEventResponse> electionStartedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ELECTIONSTARTED_EVENT));
        return electionStartedEventFlowable(filter);
    }

    public static List<GroupAdminPendingEventResponse> getGroupAdminPendingEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(GROUPADMINPENDING_EVENT, transactionReceipt);
        ArrayList<GroupAdminPendingEventResponse> responses = new ArrayList<GroupAdminPendingEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            GroupAdminPendingEventResponse typedResponse = new GroupAdminPendingEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.oldAdmin = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.newAdmin = (String) eventValues.getIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static GroupAdminPendingEventResponse getGroupAdminPendingEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(GROUPADMINPENDING_EVENT, log);
        GroupAdminPendingEventResponse typedResponse = new GroupAdminPendingEventResponse();
        typedResponse.log = log;
        typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.oldAdmin = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.newAdmin = (String) eventValues.getIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<GroupAdminPendingEventResponse> groupAdminPendingEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getGroupAdminPendingEventFromLog(log));
    }

    public Flowable<GroupAdminPendingEventResponse> groupAdminPendingEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(GROUPADMINPENDING_EVENT));
        return groupAdminPendingEventFlowable(filter);
    }

    public static List<GroupAdminUpdatedEventResponse> getGroupAdminUpdatedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(GROUPADMINUPDATED_EVENT, transactionReceipt);
        ArrayList<GroupAdminUpdatedEventResponse> responses = new ArrayList<GroupAdminUpdatedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            GroupAdminUpdatedEventResponse typedResponse = new GroupAdminUpdatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.oldAdmin = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.newAdmin = (String) eventValues.getIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static GroupAdminUpdatedEventResponse getGroupAdminUpdatedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(GROUPADMINUPDATED_EVENT, log);
        GroupAdminUpdatedEventResponse typedResponse = new GroupAdminUpdatedEventResponse();
        typedResponse.log = log;
        typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.oldAdmin = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.newAdmin = (String) eventValues.getIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<GroupAdminUpdatedEventResponse> groupAdminUpdatedEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getGroupAdminUpdatedEventFromLog(log));
    }

    public Flowable<GroupAdminUpdatedEventResponse> groupAdminUpdatedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(GROUPADMINUPDATED_EVENT));
        return groupAdminUpdatedEventFlowable(filter);
    }

    public static List<GroupCreatedEventResponse> getGroupCreatedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(GROUPCREATED_EVENT, transactionReceipt);
        ArrayList<GroupCreatedEventResponse> responses = new ArrayList<GroupCreatedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            GroupCreatedEventResponse typedResponse = new GroupCreatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static GroupCreatedEventResponse getGroupCreatedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(GROUPCREATED_EVENT, log);
        GroupCreatedEventResponse typedResponse = new GroupCreatedEventResponse();
        typedResponse.log = log;
        typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<GroupCreatedEventResponse> groupCreatedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getGroupCreatedEventFromLog(log));
    }

    public Flowable<GroupCreatedEventResponse> groupCreatedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(GROUPCREATED_EVENT));
        return groupCreatedEventFlowable(filter);
    }

    public static List<MemberAddedEventResponse> getMemberAddedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(MEMBERADDED_EVENT, transactionReceipt);
        ArrayList<MemberAddedEventResponse> responses = new ArrayList<MemberAddedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            MemberAddedEventResponse typedResponse = new MemberAddedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.index = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.identityCommitment = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.merkleTreeRoot = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static MemberAddedEventResponse getMemberAddedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(MEMBERADDED_EVENT, log);
        MemberAddedEventResponse typedResponse = new MemberAddedEventResponse();
        typedResponse.log = log;
        typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.index = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.identityCommitment = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.merkleTreeRoot = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<MemberAddedEventResponse> memberAddedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getMemberAddedEventFromLog(log));
    }

    public Flowable<MemberAddedEventResponse> memberAddedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(MEMBERADDED_EVENT));
        return memberAddedEventFlowable(filter);
    }

    public static List<MemberRemovedEventResponse> getMemberRemovedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(MEMBERREMOVED_EVENT, transactionReceipt);
        ArrayList<MemberRemovedEventResponse> responses = new ArrayList<MemberRemovedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            MemberRemovedEventResponse typedResponse = new MemberRemovedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.index = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.identityCommitment = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.merkleTreeRoot = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static MemberRemovedEventResponse getMemberRemovedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(MEMBERREMOVED_EVENT, log);
        MemberRemovedEventResponse typedResponse = new MemberRemovedEventResponse();
        typedResponse.log = log;
        typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.index = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.identityCommitment = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.merkleTreeRoot = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<MemberRemovedEventResponse> memberRemovedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getMemberRemovedEventFromLog(log));
    }

    public Flowable<MemberRemovedEventResponse> memberRemovedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(MEMBERREMOVED_EVENT));
        return memberRemovedEventFlowable(filter);
    }

    public static List<MemberUpdatedEventResponse> getMemberUpdatedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(MEMBERUPDATED_EVENT, transactionReceipt);
        ArrayList<MemberUpdatedEventResponse> responses = new ArrayList<MemberUpdatedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            MemberUpdatedEventResponse typedResponse = new MemberUpdatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.index = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.identityCommitment = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.newIdentityCommitment = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.merkleTreeRoot = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static MemberUpdatedEventResponse getMemberUpdatedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(MEMBERUPDATED_EVENT, log);
        MemberUpdatedEventResponse typedResponse = new MemberUpdatedEventResponse();
        typedResponse.log = log;
        typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.index = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.identityCommitment = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.newIdentityCommitment = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
        typedResponse.merkleTreeRoot = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
        return typedResponse;
    }

    public Flowable<MemberUpdatedEventResponse> memberUpdatedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getMemberUpdatedEventFromLog(log));
    }

    public Flowable<MemberUpdatedEventResponse> memberUpdatedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(MEMBERUPDATED_EVENT));
        return memberUpdatedEventFlowable(filter);
    }

    public static List<MembersAddedEventResponse> getMembersAddedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(MEMBERSADDED_EVENT, transactionReceipt);
        ArrayList<MembersAddedEventResponse> responses = new ArrayList<MembersAddedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            MembersAddedEventResponse typedResponse = new MembersAddedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.startIndex = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.identityCommitments = (List<BigInteger>) ((Array) eventValues.getNonIndexedValues().get(1)).getNativeValueCopy();
            typedResponse.merkleTreeRoot = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static MembersAddedEventResponse getMembersAddedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(MEMBERSADDED_EVENT, log);
        MembersAddedEventResponse typedResponse = new MembersAddedEventResponse();
        typedResponse.log = log;
        typedResponse.groupId = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.startIndex = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.identityCommitments = (List<BigInteger>) ((Array) eventValues.getNonIndexedValues().get(1)).getNativeValueCopy();
        typedResponse.merkleTreeRoot = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<MembersAddedEventResponse> membersAddedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getMembersAddedEventFromLog(log));
    }

    public Flowable<MembersAddedEventResponse> membersAddedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(MEMBERSADDED_EVENT));
        return membersAddedEventFlowable(filter);
    }

    public static List<VoteAddedEventResponse> getVoteAddedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(VOTEADDED_EVENT, transactionReceipt);
        ArrayList<VoteAddedEventResponse> responses = new ArrayList<VoteAddedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            VoteAddedEventResponse typedResponse = new VoteAddedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.ciphertextHash = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.nullifier = (BigInteger) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.ciphertext = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static VoteAddedEventResponse getVoteAddedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(VOTEADDED_EVENT, log);
        VoteAddedEventResponse typedResponse = new VoteAddedEventResponse();
        typedResponse.log = log;
        typedResponse.ciphertextHash = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.nullifier = (BigInteger) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.ciphertext = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<VoteAddedEventResponse> voteAddedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getVoteAddedEventFromLog(log));
    }

    public Flowable<VoteAddedEventResponse> voteAddedEventFlowable(DefaultBlockParameter startBlock,
            DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(VOTEADDED_EVENT));
        return voteAddedEventFlowable(filter);
    }

    public RemoteFunctionCall<BigInteger> MAX_CIPHERTEXT_BYTES() {
        final Function function = new Function(FUNC_MAX_CIPHERTEXT_BYTES, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> addVoter(BigInteger identityCommitment) {
        final Function function = new Function(
                FUNC_ADDVOTER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(identityCommitment)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> addVoters(List<BigInteger> identityCommitments) {
        final Function function = new Function(
                FUNC_ADDVOTERS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                        org.web3j.abi.datatypes.generated.Uint256.class,
                        org.web3j.abi.Utils.typeMap(identityCommitments, org.web3j.abi.datatypes.generated.Uint256.class))), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> ballotCount() {
        final Function function = new Function(FUNC_BALLOTCOUNT, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> castVote(byte[] ciphertext, BigInteger nullifier,
            List<BigInteger> proof) {
        final Function function = new Function(
                FUNC_CASTVOTE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicBytes(ciphertext), 
                new org.web3j.abi.datatypes.generated.Uint256(nullifier), 
                new org.web3j.abi.datatypes.generated.StaticArray8<org.web3j.abi.datatypes.generated.Uint256>(
                        org.web3j.abi.datatypes.generated.Uint256.class,
                        org.web3j.abi.Utils.typeMap(proof, org.web3j.abi.datatypes.generated.Uint256.class))), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<String> coordinator() {
        final Function function = new Function(FUNC_COORDINATOR, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<byte[]> encryptionPublicKey() {
        final Function function = new Function(FUNC_ENCRYPTIONPUBLICKEY, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<TransactionReceipt> endElection(byte[] decryptionMaterial) {
        final Function function = new Function(
                FUNC_ENDELECTION, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicBytes(decryptionMaterial)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> endTime() {
        final Function function = new Function(FUNC_ENDTIME, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> externalNullifier() {
        final Function function = new Function(FUNC_EXTERNALNULLIFIER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<String> getGroupAdmin(BigInteger groupId) {
        final Function function = new Function(FUNC_GETGROUPADMIN, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(groupId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<BigInteger> getMerkleTreeDepth(BigInteger groupId) {
        final Function function = new Function(FUNC_GETMERKLETREEDEPTH, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(groupId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> getMerkleTreeRoot(BigInteger groupId) {
        final Function function = new Function(FUNC_GETMERKLETREEROOT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(groupId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> getMerkleTreeSize(BigInteger groupId) {
        final Function function = new Function(FUNC_GETMERKLETREESIZE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(groupId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<Boolean> hasMember(BigInteger groupId,
            BigInteger identityCommitment) {
        final Function function = new Function(FUNC_HASMEMBER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(groupId), 
                new org.web3j.abi.datatypes.generated.Uint256(identityCommitment)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<BigInteger> indexOf(BigInteger groupId,
            BigInteger identityCommitment) {
        final Function function = new Function(FUNC_INDEXOF, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(groupId), 
                new org.web3j.abi.datatypes.generated.Uint256(identityCommitment)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<Boolean> isNullifierUsed(BigInteger nullifier) {
        final Function function = new Function(FUNC_ISNULLIFIERUSED, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(nullifier)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<TransactionReceipt> startElection() {
        final Function function = new Function(
                FUNC_STARTELECTION, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> startTime() {
        final Function function = new Function(FUNC_STARTTIME, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> state() {
        final Function function = new Function(FUNC_STATE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint8>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<String> verifier() {
        final Function function = new Function(FUNC_VERIFIER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    @Deprecated
    public static Election load(String contractAddress, Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        return new Election(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static Election load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new Election(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static Election load(String contractAddress, Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        return new Election(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static Election load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new Election(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<Election> deploy(Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider, String verifier_, String coordinator_,
            BigInteger externalNullifier_, BigInteger endTime_, byte[] encryptionPublicKey_) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, verifier_), 
                new org.web3j.abi.datatypes.Address(160, coordinator_), 
                new org.web3j.abi.datatypes.generated.Uint256(externalNullifier_), 
                new org.web3j.abi.datatypes.generated.Uint256(endTime_), 
                new org.web3j.abi.datatypes.generated.Bytes32(encryptionPublicKey_)));
        return deployRemoteCall(Election.class, web3j, credentials, contractGasProvider, getDeploymentBinary(), encodedConstructor);
    }

    public static RemoteCall<Election> deploy(Web3j web3j, TransactionManager transactionManager,
            ContractGasProvider contractGasProvider, String verifier_, String coordinator_,
            BigInteger externalNullifier_, BigInteger endTime_, byte[] encryptionPublicKey_) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, verifier_), 
                new org.web3j.abi.datatypes.Address(160, coordinator_), 
                new org.web3j.abi.datatypes.generated.Uint256(externalNullifier_), 
                new org.web3j.abi.datatypes.generated.Uint256(endTime_), 
                new org.web3j.abi.datatypes.generated.Bytes32(encryptionPublicKey_)));
        return deployRemoteCall(Election.class, web3j, transactionManager, contractGasProvider, getDeploymentBinary(), encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<Election> deploy(Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit, String verifier_, String coordinator_,
            BigInteger externalNullifier_, BigInteger endTime_, byte[] encryptionPublicKey_) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, verifier_), 
                new org.web3j.abi.datatypes.Address(160, coordinator_), 
                new org.web3j.abi.datatypes.generated.Uint256(externalNullifier_), 
                new org.web3j.abi.datatypes.generated.Uint256(endTime_), 
                new org.web3j.abi.datatypes.generated.Bytes32(encryptionPublicKey_)));
        return deployRemoteCall(Election.class, web3j, credentials, gasPrice, gasLimit, getDeploymentBinary(), encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<Election> deploy(Web3j web3j, TransactionManager transactionManager,
            BigInteger gasPrice, BigInteger gasLimit, String verifier_, String coordinator_,
            BigInteger externalNullifier_, BigInteger endTime_, byte[] encryptionPublicKey_) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, verifier_), 
                new org.web3j.abi.datatypes.Address(160, coordinator_), 
                new org.web3j.abi.datatypes.generated.Uint256(externalNullifier_), 
                new org.web3j.abi.datatypes.generated.Uint256(endTime_), 
                new org.web3j.abi.datatypes.generated.Bytes32(encryptionPublicKey_)));
        return deployRemoteCall(Election.class, web3j, transactionManager, gasPrice, gasLimit, getDeploymentBinary(), encodedConstructor);
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

    public static class ElectionEndedEventResponse extends BaseEventResponse {
        public String coordinator;

        public byte[] decryptionMaterial;
    }

    public static class ElectionStartedEventResponse extends BaseEventResponse {
        public String coordinator;

        public BigInteger startTime;

        public BigInteger endTime;
    }

    public static class GroupAdminPendingEventResponse extends BaseEventResponse {
        public BigInteger groupId;

        public String oldAdmin;

        public String newAdmin;
    }

    public static class GroupAdminUpdatedEventResponse extends BaseEventResponse {
        public BigInteger groupId;

        public String oldAdmin;

        public String newAdmin;
    }

    public static class GroupCreatedEventResponse extends BaseEventResponse {
        public BigInteger groupId;
    }

    public static class MemberAddedEventResponse extends BaseEventResponse {
        public BigInteger groupId;

        public BigInteger index;

        public BigInteger identityCommitment;

        public BigInteger merkleTreeRoot;
    }

    public static class MemberRemovedEventResponse extends BaseEventResponse {
        public BigInteger groupId;

        public BigInteger index;

        public BigInteger identityCommitment;

        public BigInteger merkleTreeRoot;
    }

    public static class MemberUpdatedEventResponse extends BaseEventResponse {
        public BigInteger groupId;

        public BigInteger index;

        public BigInteger identityCommitment;

        public BigInteger newIdentityCommitment;

        public BigInteger merkleTreeRoot;
    }

    public static class MembersAddedEventResponse extends BaseEventResponse {
        public BigInteger groupId;

        public BigInteger startIndex;

        public List<BigInteger> identityCommitments;

        public BigInteger merkleTreeRoot;
    }

    public static class VoteAddedEventResponse extends BaseEventResponse {
        public byte[] ciphertextHash;

        public BigInteger nullifier;

        public byte[] ciphertext;
    }
}
