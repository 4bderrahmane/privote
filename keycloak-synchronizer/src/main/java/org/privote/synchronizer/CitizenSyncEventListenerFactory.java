package org.privote.synchronizer;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class CitizenSyncEventListenerFactory implements EventListenerProviderFactory
{

    @Override
    public String getId()
    {
        return "citizen-sync";
    }

    // This method is called every time an event happens.
    // It creates a new instance of the Listener, passing the current Session.
    @Override
    public EventListenerProvider create(KeycloakSession session)
    {
        return new org.privote.synchronizer.CitizenSyncEventListener(session);
    }

    @Override
    public void init(Config.Scope scope)
    {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory)
    {
    }

    @Override
    public void close()
    {
    }
}
