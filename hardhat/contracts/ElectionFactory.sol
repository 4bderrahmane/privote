// SPDX-License-Identifier: MIT
pragma solidity >=0.8.23 <0.9.0;

import "./Election.sol";
import "@semaphore-protocol/contracts/interfaces/ISemaphoreVerifier.sol";

/// @title ElectionFactory
/// @notice Deploys one SemaphoreElectionInstance per election.

contract ElectionFactory {

    error Factory__InvalidVerifier();
    error Factory__ElectionAlreadyExists();

    /// @dev Emitted when a new election instance is deployed.
    /// @param uuid: Off-chain UUID (bytes16).
    /// @param externalNullifier: External nullifier derived from uuid (raw uint256 scope).
    /// @param coordinator: Coordinator of this election (msg.sender).
    /// @param election: Deployed election contract address.

    event ElectionDeployed(
        bytes16 indexed uuid,
        uint256 indexed externalNullifier,
        address indexed coordinator,
        address election,
        uint256 endTime
    );

    /// @notice The Groth16 semaphore verifier contract address (shared by all elections).
    address public immutable verifier;

    /// @dev UUID -> election instance address.
    mapping(bytes16 => address) public electionByUuid;

    constructor(address verifier_) {
        if (verifier_ == address(0) || verifier_.code.length == 0) {
            revert Factory__InvalidVerifier();
        }
        verifier = verifier_;
    }

    function createElection(bytes16 uuid, uint256 endTime, bytes32 encryptionPublicKey) external returns (address election) {
        if (electionByUuid[uuid] != address(0)) revert Factory__ElectionAlreadyExists();

        // Use the raw UUID as the scope so on-chain hashing matches Semaphore's convention.
        uint256 externalNullifier = _uuidToExternalNullifier(uuid);

        election = address(new Election(
            ISemaphoreVerifier(verifier),
            msg.sender,       // coordinator
            externalNullifier,
            endTime,
            encryptionPublicKey
        ));

        electionByUuid[uuid] = election;

        emit ElectionDeployed(uuid, externalNullifier, msg.sender, election, endTime);
    }

    /// @dev Cast bytes16 -> uint256 scope (no hashing).
    function _uuidToExternalNullifier(bytes16 uuid) internal pure returns (uint256) {
        return uint256(uint128(uuid));
    }
}