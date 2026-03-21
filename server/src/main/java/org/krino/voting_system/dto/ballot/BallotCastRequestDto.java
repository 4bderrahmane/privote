package org.krino.voting_system.dto.ballot;

import lombok.Data;

import java.util.List;

@Data
public class BallotCastRequestDto
{
    private byte[] ciphertext;
    private String nullifier;
    private List<String> proof;
}
