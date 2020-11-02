package com.charlyghislain.keycloak.importexport;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class KeycloakImportExportRestResourceFactory implements RealmResourceProviderFactory {
    public final static String PROVIDER_ID = "importexport";

    public RealmResourceProvider create(KeycloakSession session) {
        return new KeycloakImportExportRestResourceProvider(session);
    }

    public void init(Config.Scope config) {
    }

    public void postInit(KeycloakSessionFactory factory) {

    }

    public void close() {
    }

    public String getId() {
        return PROVIDER_ID;
    }
}
