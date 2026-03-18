import {createPublicClient, getContract, http, parseAbi, isAddress, Address} from "viem"
import { env } from "../config/env"

export const client = createPublicClient({
    transport: http(env.RPC_URL)
})

export const ELECTION_ABI = parseAbi([
    "function externalNullifier() view returns (uint256)",
    "function getMerkleTreeDepth(uint256 groupId) view returns (uint256)",
    "function getMerkleTreeRoot(uint256 groupId) view returns (uint256)",
    "event MemberAdded(uint256 indexed groupId, uint256 index, uint256 identityCommitment, uint256 merkleTreeRoot)"
] as const)

export const ELECTION_FACTORY_ABI = parseAbi([
    "event ElectionDeployed(bytes16 indexed uuid, uint256 indexed externalNullifier, address indexed coordinator, address election, uint256 endTime)"
] as const)

export function electionContract(address: Address) {
    return getContract({
        address,
        abi: ELECTION_ABI,
        client
    })
}

export function electionFactoryContract(address: Address) {
    return getContract({
        address,
        abi: ELECTION_FACTORY_ABI,
        client
    })
}

export function requireAddress(value: string): Address {
    if (!isAddress(value)) throw new Error("Invalid address")
    return value;
}
