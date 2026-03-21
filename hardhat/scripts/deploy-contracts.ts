import fs from "node:fs/promises";
import path from "node:path";
import { network } from "hardhat";
import type { InterfaceAbi } from "ethers";

const { ethers } = await network.connect();

const POSEIDON_T3 = "npm/poseidon-solidity@0.0.5/PoseidonT3.sol:PoseidonT3";
const [POSEIDON_T3_SOURCE, POSEIDON_T3_NAME] = POSEIDON_T3.split(":");

type Artifact = { abi: InterfaceAbi; bytecode: string };

let cachedPoseidonArtifact: Artifact | undefined;

function requireHex32(value: string, name: string): string {
  if (!ethers.isHexString(value, 32)) {
    throw new Error(
      `${name} must be a 32-byte hex string (bytes32). Got: ${value}`
    );
  }
  return value;
}

async function loadPoseidonArtifact(): Promise<Artifact> {
  if (cachedPoseidonArtifact) return cachedPoseidonArtifact;

  const buildInfoDir = path.join(process.cwd(), "artifacts", "build-info");
  const entries = await fs.readdir(buildInfoDir);

  for (const entry of entries) {
    if (!entry.endsWith(".output.json") && !entry.endsWith(".json")) continue;

    const buildInfoPath = path.join(buildInfoDir, entry);
    const buildInfo = JSON.parse(await fs.readFile(buildInfoPath, "utf8"));
    const poseidon =
      buildInfo?.output?.contracts?.[POSEIDON_T3_SOURCE]?.[POSEIDON_T3_NAME];

    if (poseidon?.abi && poseidon?.evm?.bytecode?.object) {
      const bytecodeObject = poseidon.evm.bytecode.object as string;
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
  const poseidonT3 = await PoseidonT3.deploy();
  await poseidonT3.waitForDeployment();
  return poseidonT3;
}

async function deployVerifierIfNeeded() {
  const envVerifier = process.env.SEMAPHORE_VERIFIER_ADDRESS;
  if (envVerifier && envVerifier.trim().length > 0) {
    if (!ethers.isAddress(envVerifier)) {
      throw new Error(
        `SEMAPHORE_VERIFIER_ADDRESS must be an address. Got: ${envVerifier}`
      );
    }
    return {
      verifierAddress: envVerifier,
      deployedMock: undefined as string | undefined,
    };
  }

  const Verifier = await ethers.getContractFactory(
    "contracts/test/MockGroth16Verifier.sol:MockGroth16Verifier"
  );
  const verifier = await Verifier.deploy();
  await verifier.waitForDeployment();
  return {
    verifierAddress: verifier.target as string,
    deployedMock: verifier.target as string,
  };
}

async function main() {
  const [deployer] = await ethers.getSigners();
  const net = await ethers.provider.getNetwork();

  const poseidonT3 = await deployPoseidonT3();
  const { verifierAddress, deployedMock } = await deployVerifierIfNeeded();

  const Factory = await ethers.getContractFactory(
    "contracts/ElectionFactory.sol:ElectionFactory",
    {
      libraries: {
        [POSEIDON_T3]: poseidonT3.target,
      },
    }
  );

  const factory = await Factory.deploy(verifierAddress);
  await factory.waitForDeployment();

  const deployElectionEnv = process.env.DEPLOY_ELECTION?.trim().toLowerCase();
  const deployElection =
    deployElectionEnv === "1" || deployElectionEnv === "true";

  let electionAddress: string | undefined;
  let electionArgs:
    | {
        coordinator: string;
        externalNullifier: string;
        endTime: string;
        encryptionPublicKey: string;
      }
    | undefined;

  if (deployElection) {
    const coordinator = process.env.COORDINATOR_ADDRESS?.trim();
    const coordinatorAddress =
      coordinator && coordinator.length > 0 ? coordinator : deployer.address;
    if (!ethers.isAddress(coordinatorAddress)) {
      throw new Error(
        `COORDINATOR_ADDRESS must be an address. Got: ${coordinatorAddress}`
      );
    }

    const now = (await ethers.provider.getBlock("latest"))!.timestamp;
    const endTimeEnv = process.env.END_TIME?.trim();
    const durationEnv = process.env.DURATION_SECONDS?.trim();

    const endTime = endTimeEnv
      ? BigInt(endTimeEnv)
      : BigInt(now + Number(durationEnv ?? "3600"));

    const externalNullifierEnv = process.env.EXTERNAL_NULLIFIER?.trim();
    const externalNullifier = externalNullifierEnv
      ? BigInt(externalNullifierEnv)
      : 123n;

    const encryptionPublicKeyEnv = process.env.ENCRYPTION_PUBLIC_KEY?.trim();
    const encryptionPublicKey = encryptionPublicKeyEnv
      ? requireHex32(encryptionPublicKeyEnv, "ENCRYPTION_PUBLIC_KEY")
      : ethers.keccak256(ethers.toUtf8Bytes("pubkey"));

    const Election = await ethers.getContractFactory(
      "contracts/Election.sol:Election",
      {
        libraries: {
          [POSEIDON_T3]: poseidonT3.target,
        },
      }
    );

    const election = await Election.deploy(
      verifierAddress,
      coordinatorAddress,
      externalNullifier,
      endTime,
      encryptionPublicKey
    );
    await election.waitForDeployment();

    electionAddress = election.target as string;
    electionArgs = {
      coordinator: coordinatorAddress,
      externalNullifier: externalNullifier.toString(),
      endTime: endTime.toString(),
      encryptionPublicKey,
    };
  }

  console.log("Deployer:", deployer.address);
  console.log("Network:", { chainId: net.chainId.toString() });
  console.log("PoseidonT3:", poseidonT3.target);
  if (deployedMock) console.log("MockGroth16Verifier:", deployedMock);
  console.log("SemaphoreVerifier:", verifierAddress);
  console.log("ElectionFactory:", factory.target);
  if (electionAddress) {
    console.log("Election:", electionAddress);
    console.log("Election args:", electionArgs);
  }
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
