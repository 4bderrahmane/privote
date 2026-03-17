import fs from "node:fs/promises";
import path from "node:path";
import { expect } from "chai";
import { network } from "hardhat";
import type { BytesLike, InterfaceAbi } from "ethers";
import type {
  Election as ElectionType,
  MockGroth16Verifier as MockGroth16VerifierType,
} from "../types/ethers-contracts/index.js";

const { ethers } = await network.connect();

const POSEIDON_T3 = "npm/poseidon-solidity@0.0.5/PoseidonT3.sol:PoseidonT3";
const [POSEIDON_T3_SOURCE, POSEIDON_T3_NAME] = POSEIDON_T3.split(":");

let cachedPoseidonArtifact: { abi: InterfaceAbi; bytecode: string } | undefined;

describe("Election", function () {
  async function loadPoseidonArtifact() {
    if (cachedPoseidonArtifact) {
      return cachedPoseidonArtifact;
    }

    const buildInfoDir = path.join(process.cwd(), "artifacts", "build-info");
    const entries = await fs.readdir(buildInfoDir);

    for (const entry of entries) {
      if (!entry.endsWith(".output.json") && !entry.endsWith(".json")) {
        continue;
      }

      const buildInfoPath = path.join(buildInfoDir, entry);
      const buildInfo = JSON.parse(await fs.readFile(buildInfoPath, "utf8"));
      const poseidon =
        buildInfo?.output?.contracts?.[POSEIDON_T3_SOURCE]?.[POSEIDON_T3_NAME];

      if (poseidon?.abi && poseidon?.evm?.bytecode?.object) {
        const bytecodeObject = poseidon.evm.bytecode.object;
        const bytecode = bytecodeObject.startsWith("0x")
          ? bytecodeObject
          : `0x${bytecodeObject}`;

        const abi = poseidon.abi as InterfaceAbi;
        cachedPoseidonArtifact = { abi, bytecode };
        return cachedPoseidonArtifact;
      }
    }

    throw new Error(
      "PoseidonT3 build output not found in artifacts/build-info. Run `npx hardhat compile` first."
    );
  }

  async function deployPoseidonT3() {
    const [deployer] = await ethers.getSigners();
    const { abi, bytecode } = await loadPoseidonArtifact();

    const PoseidonT3 = new ethers.ContractFactory(abi, bytecode, deployer);
    const poseidon = await PoseidonT3.deploy();
    await poseidon.waitForDeployment();

    return poseidon;
  }

  async function deployElection() {
    const [coordinator, other] = await ethers.getSigners();

    const poseidonT3 = await deployPoseidonT3();

    const Verifier = await ethers.getContractFactory(
      "contracts/test/MockGroth16Verifier.sol:MockGroth16Verifier"
    );
    const verifier = (await Verifier.deploy()) as MockGroth16VerifierType;
    await verifier.waitForDeployment();

    const now = (await ethers.provider.getBlock("latest"))!.timestamp;
    const endTime = BigInt(now + 3600);
    const externalNullifier = 123n;
    const encryptionPublicKey = ethers.keccak256(ethers.toUtf8Bytes("pubkey"));

    const ElectionFactory = await ethers.getContractFactory(
      "contracts/Election.sol:Election",
      {
        libraries: {
          [POSEIDON_T3]: poseidonT3.target,
        },
      }
    );

    const election = (await ElectionFactory.deploy(
      verifier.target,
      coordinator.address,
      externalNullifier,
      endTime,
      encryptionPublicKey
    )) as ElectionType;
    await election.waitForDeployment();

    return {
      election,
      verifier,
      coordinator,
      other,
      externalNullifier,
      endTime,
      encryptionPublicKey,
    };
  }

  it("initializes constructor values", async function () {
    const {
      election,
      verifier,
      coordinator,
      externalNullifier,
      endTime,
      encryptionPublicKey,
    } = await deployElection();

    expect(await election.verifier()).to.equal(verifier.target);
    expect(await election.coordinator()).to.equal(coordinator.address);
    expect(await election.externalNullifier()).to.equal(externalNullifier);
    expect(await election.endTime()).to.equal(endTime);
    expect(await election.encryptionPublicKey()).to.equal(encryptionPublicKey);
    expect(await election.state()).to.equal(0n);
    expect(await election.startTime()).to.equal(0n);
    expect(await election.ballotCount()).to.equal(0n);
  });

  it("adds voters only during registration", async function () {
    const { election, other, externalNullifier } = await deployElection();
    const identityCommitment = 1n;

    const electionAsOther = election.connect(other) as ElectionType;

    await expect(
      electionAsOther.addVoter(identityCommitment)
    ).to.be.revertedWithCustomError(
      election,
      "Election__CallerIsNotCoordinator"
    );

    await election.addVoter(identityCommitment);

    expect(
      await election.hasMember(externalNullifier, identityCommitment)
    ).to.equal(true);

    await expect(
      election.addVoter(identityCommitment)
    ).to.be.revertedWithCustomError(
      election,
      "Election__MemberAlreadyExists"
    );
  });

  it("adds multiple voters during registration", async function () {
    const { election, externalNullifier } = await deployElection();
    const identityCommitments = [1n, 2n, 3n];

    await election.addVoters(identityCommitments);

    for (const identityCommitment of identityCommitments) {
      expect(
        await election.hasMember(externalNullifier, identityCommitment)
      ).to.equal(true);
    }
  });

  it("starts the election and locks registration", async function () {
    const { election, other, coordinator, endTime } = await deployElection();

    const electionAsOther = election.connect(other) as ElectionType;

    await expect(
      electionAsOther.startElection()
    ).to.be.revertedWithCustomError(
      election,
      "Election__CallerIsNotCoordinator"
    );

    const tx = await election.startElection();
    const receipt = await tx.wait();
    const block = await ethers.provider.getBlock(receipt!.blockNumber);

    await expect(tx)
      .to.emit(election, "ElectionStarted")
      .withArgs(coordinator.address, BigInt(block!.timestamp), endTime);

    expect(await election.state()).to.equal(1n);
    expect(await election.startTime()).to.equal(BigInt(block!.timestamp));

    await expect(
      election.startElection()
    ).to.be.revertedWithCustomError(
      election,
      "Election__NotInRegistrationPhase"
    );
  });

  it("rejects adding voters after the election has started", async function () {
    const { election } = await deployElection();

    await election.startElection();

    await expect(
      election.addVoter(1n)
    ).to.be.revertedWithCustomError(
      election,
      "Election__NotInRegistrationPhase"
    );
  });

  it("casts a vote", async function () {
    const { election } = await deployElection();

    const ciphertext = ethers.toUtf8Bytes("vote");
    const ciphertextHex = ethers.hexlify(ciphertext);
    const ciphertextHash = ethers.keccak256(ciphertextHex);
    const nullifier = 9n;
    const proof = Array(8).fill(0n);

    await election.startElection();

    await expect(
      election.castVote(ciphertext, nullifier, proof)
    ).to.emit(election, "VoteAdded")
      .withArgs(ciphertextHash, nullifier, ciphertextHex);

    expect(await election.isNullifierUsed(nullifier)).to.equal(true);
    expect(await election.ballotCount()).to.equal(1n);
  });

  it("rejects reused nullifiers", async function () {
    const { election } = await deployElection();

    const ciphertext = ethers.toUtf8Bytes("vote");
    const nullifier = 10n;
    const proof = Array(8).fill(0n);

    await election.startElection();
    await election.castVote(ciphertext, nullifier, proof);

    await expect(
      election.castVote(ciphertext, nullifier, proof)
    ).to.be.revertedWithCustomError(
      election,
      "Election__NullifierAlreadyUsed"
    );
  });

  it("rejects invalid proofs", async function () {
    const { election, verifier } = await deployElection();

    const ciphertext = ethers.toUtf8Bytes("vote");
    const nullifier = 11n;
    const proof = Array(8).fill(0n);

    await election.startElection();
    await verifier.setShouldVerify(false);

    await expect(
      election.castVote(ciphertext, nullifier, proof)
    ).to.be.revertedWithCustomError(
      election,
      "Election__InvalidProof"
    );
  });

  it("rejects voting before the election starts", async function () {
    const { election } = await deployElection();

    const ciphertext = ethers.toUtf8Bytes("vote");
    const nullifier = 12n;
    const proof = Array(8).fill(0n);

    await expect(
      election.castVote(ciphertext, nullifier, proof)
    ).to.be.revertedWithCustomError(
      election,
      "Election__NotInVotingPhase"
    );
  });

  it("rejects empty ciphertext", async function () {
    const { election } = await deployElection();

    const nullifier = 13n;
    const proof = Array(8).fill(0n);

    await election.startElection();

    await expect(
      election.castVote("0x", nullifier, proof)
    ).to.be.revertedWithCustomError(
      election,
      "Election__EmptyCiphertext"
    );
  });

  it("rejects oversized ciphertext", async function () {
    const { election } = await deployElection();

    const oversized = "0x" + "11".repeat(1025);
    const nullifier = 14n;
    const proof = Array(8).fill(0n);

    await election.startElection();

    await expect(
      election.castVote(oversized, nullifier, proof)
    ).to.be.revertedWithCustomError(
      election,
      "Election__CiphertextTooLarge"
    );
  });

  it("ends the election after the end time", async function () {
    const { election, coordinator, endTime } = await deployElection();
    const decryptionMaterial = "0x1234";

    await election.startElection();

    await expect(
      election.endElection(decryptionMaterial)
    ).to.be.revertedWithCustomError(
      election,
      "Election__ElectionHasNotEndedYet"
    );

    await ethers.provider.send("evm_setNextBlockTimestamp", [
      Number(endTime) + 1,
    ]);
    await ethers.provider.send("evm_mine", []);

    await expect(
      election.endElection(decryptionMaterial)
    ).to.emit(election, "ElectionEnded")
      .withArgs(coordinator.address, decryptionMaterial);

    expect(await election.state()).to.equal(2n);
  });

  it("rejects voting after the end time", async function () {
    const { election, endTime } = await deployElection();

    const ciphertext = ethers.toUtf8Bytes("vote");
    const nullifier = 15n;
    const proof = Array(8).fill(0n);

    await election.startElection();

    await ethers.provider.send("evm_setNextBlockTimestamp", [
      Number(endTime) + 1,
    ]);
    await ethers.provider.send("evm_mine", []);

    await expect(
      election.castVote(ciphertext, nullifier, proof)
    ).to.be.revertedWithCustomError(
      election,
      "Election__ElectionHasEnded"
    );
  });
});