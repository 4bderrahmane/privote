// SPDX-License-Identifier: MIT
pragma solidity >=0.8.23 <0.9.0;

contract MockGroth16Verifier {
    bool public shouldVerify = true;

    function setShouldVerify(bool value) external {
        shouldVerify = value;
    }

    function verifyProof(
        uint256[2] calldata,
        uint256[2][2] calldata,
        uint256[2] calldata,
        uint256[4] calldata
    ) external view returns (bool) {
        return shouldVerify;
    }
}