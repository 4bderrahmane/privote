// SPDX-License-Identifier: MIT
pragma solidity >=0.8.23 <0.9.0;

/// @title Election contract interface (single election instance).
interface IElection {

    error Election__CallerIsNotCoordinator();
    error Election__NotInRegistrationPhase();
    error Election__NotInVotingPhase();
    error Election__InvalidExternalNullifier();
    error Election__InvalidProof();
    error Election__InvalidEndTime();
    error Election__ElectionHasEnded();
    error Election__ElectionHasNotEndedYet();
    error Election__InvalidVerifier();
    error Election__MemberAlreadyExists();
    error Election__NullifierAlreadyUsed();
    error Election__InvalidCoordinator();
    error Election__InvalidEncryptionPublicKey();
    error Election__EmptyCiphertext();
    error Election__CiphertextTooLarge();
    error Election__VotingWindowElapsed();

    enum ElectionPhase {
        REGISTRATION,
        VOTING,
        TALLY
    }

    // Events

    /// @dev Emitted when an election is started.
    /// @param coordinator Coordinator of the election.
    /// @param startTime Election start time.
    /// @param endTime Election end time.
    event ElectionStarted(
        address indexed coordinator,
        uint256 startTime,
        uint256 endTime
    );

    /// @dev Emitted when a user votes in an election.
    /// @param ciphertextHash Hash of the user's encrypted vote.
    /// @param nullifier Nullifier produced by the ZK proof and used to prevent double-voting.
    /// @param ciphertext User encrypted vote.
    event VoteAdded(
        bytes32 indexed ciphertextHash,
        uint256 indexed nullifier,
        bytes ciphertext
    );

    /// @dev Emitted when an election is ended.
    /// @param coordinator Coordinator of the election.
    /// @param decryptionMaterial Material later used to decrypt / open the election ballots.
    /// This is intentionally generic because the project may evolve from a single-key model
    /// to threshold decryption or another tally-opening mechanism.
    event ElectionEnded(
        address indexed coordinator,
        bytes decryptionMaterial
    );

    // Functions

    /// @dev Returns the Groth16 verifier contract address used by this election.
    function verifier() external view returns (address);

    /// @dev Returns the election coordinator.
    function coordinator() external view returns (address);

    /// @dev Returns the value used both as:
    /// 1) the Semaphore group id
    /// 2) the nullifier scope / external nullifier domain
    function externalNullifier() external view returns (uint256);

    /// @dev Returns the timestamp at which the election was started.
    /// It is zero until `startElection()` is called.
    function startTime() external view returns (uint256);

    /// @dev Returns the timestamp after which voting is no longer allowed.
    function endTime() external view returns (uint256);

    /// @dev Returns the election public key used by voters to encrypt their ballots.
    function encryptionPublicKey() external view returns (bytes32);

    /// @dev Returns the current election phase.
    function state() external view returns (ElectionPhase);

    /// @dev Returns the number of accepted ballots.
    function ballotCount() external view returns (uint256);

    /// @dev Adds a voter to an election.
    /// @param identityCommitment Identity commitment of the group member.
    function addVoter(uint256 identityCommitment) external;

    /// @dev Adds more than one voter to an election.
    /// @param identityCommitments Identity commitments of the group members.
    function addVoters(uint256[] calldata identityCommitments) external;

    /// @dev Starts the election voting phase.
    /// The encryption public key is already set at deployment time,
    /// so this function only transitions the election from REGISTRATION to VOTING.
    function startElection() external;

    /// @dev Casts an anonymous vote in an election.
    /// @param ciphertext Encrypted vote.
    /// @param nullifier Nullifier produced by the proof. It is unique per identity and scope.
    /// @param proof Private zk-proof parameters.
    ///
    /// Note:
    /// The proof is bound to the encrypted ballot through `message = hash(ciphertext)`,
    /// not to the plaintext vote itself. This preserves ballot privacy while still
    /// preventing proof reuse with a different ciphertext.
    function castVote(
        bytes calldata ciphertext,
        uint256 nullifier,
        uint256[8] calldata proof
    ) external;

    /// @dev Ends an election and publishes the material used later for tally/decryption.
    /// @param decryptionMaterial Material used to open / decrypt the election ballots.
    function endElection(bytes calldata decryptionMaterial) external;

    /// @dev Checks if a nullifier has already been used in this election.
    /// @param nullifier Nullifier produced by the proof.
    function isNullifierUsed(uint256 nullifier) external view returns (bool);
}