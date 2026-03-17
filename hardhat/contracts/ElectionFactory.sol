// SPDX-License-Identifier: MIT
pragma solidity >=0.8.23 <0.9.0;

import "./Election.sol";
import "./interfaces/IGroth16Verifier.sol";

contract ElectionFactory {

    error Factory__InvalidVerifier();
    error Factory__ElectionAlreadyExists();
    error Factory__InvalidUuid();
    error Factory__InvalidEncryptionPublicKey();

    /// @dev Emitted when a new election instance is deployed.
    /// @param uuid Off-chain UUID (bytes16).
    /// @param externalNullifier Value derived from uuid and used in this project as:
    /// 1) the Semaphore group id
    /// 2) the nullifier scope / external nullifier domain
    /// @param coordinator Coordinator of this election (msg.sender).
    /// @param election Deployed election contract address.
    /// @param endTime End time of election.
    event ElectionDeployed(
        bytes16 indexed uuid,
        uint256 indexed externalNullifier,
        address indexed coordinator,
        address election,
        uint256 endTime
    );

    /// @notice The Groth16 verifier contract address shared by all elections.
    address public immutable verifier;

    /// @dev UUID -> election instance address.
    mapping(bytes16 => address) public electionByUuid;

    constructor(address verifier_) {
        if (verifier_ == address(0) || verifier_.code.length == 0) {
            revert Factory__InvalidVerifier();
        }

        verifier = verifier_;
    }

    function createElection(
        bytes16 uuid,
        uint256 endTime,
        bytes32 encryptionPublicKey
    ) external returns (address election) {
        if (uuid == bytes16(0)) revert Factory__InvalidUuid();
        if (electionByUuid[uuid] != address(0)) revert Factory__ElectionAlreadyExists();
        if (encryptionPublicKey == bytes32(0)) revert Factory__InvalidEncryptionPublicKey();

        // Keep your original simple model:
        // the raw UUID is cast to uint256 and used directly as the election external nullifier.
        //
        // In the Election contract, this same value is used both as:
        // 1) the Semaphore group id
        // 2) the scope / external nullifier domain of the proof
        uint256 externalNullifier_ = _uuidToExternalNullifier(uuid);

        election = address(
            new Election(
                IGroth16Verifier(verifier),
                msg.sender, // coordinator
                externalNullifier_,
                endTime,
                encryptionPublicKey
            )
        );

        electionByUuid[uuid] = election;

        emit ElectionDeployed(
            uuid,
            externalNullifier_,
            msg.sender,
            election,
            endTime
        );
    }

    /// @dev Cast bytes16 -> uint256 scope/group id (no hashing).
    function _uuidToExternalNullifier(bytes16 uuid) internal pure returns (uint256) {
        return uint256(uint128(uuid));
    }
}