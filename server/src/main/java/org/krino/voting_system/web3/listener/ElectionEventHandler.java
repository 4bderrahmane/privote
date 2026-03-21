package org.krino.voting_system.web3.listener;

import org.krino.voting_system.web3.listener.events.ElectionDeployedEvent;
import org.krino.voting_system.web3.listener.events.ElectionEndedEvent;
import org.krino.voting_system.web3.listener.events.ElectionStartedEvent;
import org.krino.voting_system.web3.listener.events.MemberAddedEvent;

public interface ElectionEventHandler
{
    void onElectionDeployed(ElectionDeployedEvent event);

    void onMemberAdded(MemberAddedEvent event);

    void onElectionStarted(ElectionStartedEvent event);

    void onElectionEnded(ElectionEndedEvent event);
}
