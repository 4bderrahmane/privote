package org.privote.backend.web3.listener;

import org.privote.backend.web3.listener.events.ElectionDeployedEvent;
import org.privote.backend.web3.listener.events.ElectionEndedEvent;
import org.privote.backend.web3.listener.events.ElectionStartedEvent;
import org.privote.backend.web3.listener.events.MemberAddedEvent;

public interface ElectionEventHandler
{
    void onElectionDeployed(ElectionDeployedEvent event);

    void onMemberAdded(MemberAddedEvent event);

    void onElectionStarted(ElectionStartedEvent event);

    void onElectionEnded(ElectionEndedEvent event);
}
