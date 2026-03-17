import fs from "node:fs/promises";
import path from "node:path";
import { expect } from "chai";
import { network } from "hardhat";
import type { InterfaceAbi } from "ethers";
import type {
  Election,
  ElectionFactory,
  MockGroth16Verifier,
} from "../types/ethers-contracts/index.js";

const { ethers } = await network.connect();

const POSEIDON_T3 = "npm/poseidon-solidity@0.0.5/PoseidonT3.sol:PoseidonT3";
const [POSEIDON_T3_SOURCE, POSEIDON_T3_NAME] = POSEIDON_T3.split(":");

let cachedPoseidonArtifact: { abi: InterfaceAbi; bytecode: string } | undefined;

describe("ElectionFactory", function () {
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

  async function deployFactory() {
    const [, other] = await ethers.getSigners();

    const poseidonT3 = await deployPoseidonT3();

    // For factory tests, the verifier only needs to exist and have code.
    // The actual proof semantics are tested later in Election integration tests.
    const Verifier = await ethers.getContractFactory(
      "contracts/test/MockGroth16Verifier.sol:MockGroth16Verifier"
    );
    const verifier = (await Verifier.deploy()) as MockGroth16Verifier;
    await verifier.waitForDeployment();

    const Factory = await ethers.getContractFactory(
      "contracts/ElectionFactory.sol:ElectionFactory",
      {
        libraries: {
          [POSEIDON_T3]: poseidonT3.target,
        },
      }
    );

    const factory = (await Factory.deploy(verifier.target)) as ElectionFactory;
    await factory.waitForDeployment();

    return { factory, verifier, other, poseidonT3 };
  }

  it("stores the verifier address", async function () {
    const { factory, verifier } = await deployFactory();
    expect(await factory.verifier()).to.equal(verifier.target);
  });

  it("reverts if deployed with a zero verifier", async function () {
    const poseidonT3 = await deployPoseidonT3();

    const Factory = await ethers.getContractFactory(
      "contracts/ElectionFactory.sol:ElectionFactory",
      {
        libraries: {
          [POSEIDON_T3]: poseidonT3.target,
        },
      }
    );

    await expect(
      Factory.deploy(ethers.ZeroAddress)
    ).to.be.revertedWithCustomError(Factory, "Factory__InvalidVerifier");
  });

  it("creates elections and stores the deployment", async function () {
    const { factory, other, poseidonT3 } = await deployFactory();

    const now = (await ethers.provider.getBlock("latest"))!.timestamp;
    const endTime = BigInt(now + 3600);
    const uuid = ethers.hexlify(ethers.randomBytes(16));
    const encryptionPublicKey = ethers.keccak256(ethers.toUtf8Bytes("pubkey"));

    const factoryAsOther = factory.connect(other) as ElectionFactory;

    await expect(
      factoryAsOther.createElection(uuid, endTime, encryptionPublicKey)
    ).to.emit(factory, "ElectionDeployed");

    const electionAddress = await factory.electionByUuid(uuid);
    expect(electionAddress).to.not.equal(ethers.ZeroAddress);

    const ElectionContractFactory = await ethers.getContractFactory(
      "contracts/Election.sol:Election",
      {
        libraries: {
          [POSEIDON_T3]: poseidonT3.target,
        },
      }
    );

    const election = ElectionContractFactory.attach(electionAddress) as Election;

    expect(await election.coordinator()).to.equal(other.address);
    expect(await election.externalNullifier()).to.equal(BigInt(uuid));
    expect(await election.endTime()).to.equal(endTime);
    expect(await election.encryptionPublicKey()).to.equal(encryptionPublicKey);
  });

  it("prevents duplicate elections", async function () {
    const { factory, other } = await deployFactory();

    const now = (await ethers.provider.getBlock("latest"))!.timestamp;
    const endTime = BigInt(now + 3600);
    const uuid = ethers.hexlify(ethers.randomBytes(16));
    const encryptionPublicKey = ethers.keccak256(ethers.toUtf8Bytes("pubkey"));

    const factoryAsOther = factory.connect(other) as ElectionFactory;

    await factoryAsOther.createElection(uuid, endTime, encryptionPublicKey);

    await expect(
      factory.createElection(uuid, endTime, encryptionPublicKey)
    ).to.be.revertedWithCustomError(factory, "Factory__ElectionAlreadyExists");
  });

  it("reverts for zero uuid", async function () {
    const { factory } = await deployFactory();

    const now = (await ethers.provider.getBlock("latest"))!.timestamp;
    const endTime = BigInt(now + 3600);
    const encryptionPublicKey = ethers.keccak256(ethers.toUtf8Bytes("pubkey"));

    await expect(
      factory.createElection(
        ethers.ZeroHash.slice(0, 34), // bytes16
        endTime,
        encryptionPublicKey
      )
    ).to.be.revertedWithCustomError(factory, "Factory__InvalidUuid");
  });

  it("reverts for zero encryption public key", async function () {
    const { factory } = await deployFactory();

    const now = (await ethers.provider.getBlock("latest"))!.timestamp;
    const endTime = BigInt(now + 3600);
    const uuid = ethers.hexlify(ethers.randomBytes(16));

    await expect(
      factory.createElection(uuid, endTime, ethers.ZeroHash)
    ).to.be.revertedWithCustomError(
      factory,
      "Factory__InvalidEncryptionPublicKey"
    );
  });
});