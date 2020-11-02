package com.charlyghislain.keycloak.importexport;

import org.keycloak.Config;
import org.keycloak.exportimport.Strategy;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.resources.KeycloakApplication;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ImportExportUtils {
    private final static Logger logger = Logger.getLogger(ImportExportUtils.class.getName());

    public static RealmModel importRealm(KeycloakSession session, KeycloakApplication keycloak,
                                         RealmRepresentation rep, Strategy strategy, boolean skipUserDependent) {
        String realmName = rep.getRealm();
        RealmProvider model = session.realms();
        RealmModel realm = model.getRealmByName(realmName);

        if (realm != null) {
            if (strategy == Strategy.IGNORE_EXISTING) {
                logger.log(Level.INFO, "Realm '%s' already exists. Import skipped", realmName);
                return null;
            } else if (strategy == Strategy.OVERWRITE_EXISTING) {
                logger.log(Level.WARNING, "Realm '%s' already exists. Removing it before import", realmName);
                if (Config.getAdminRealm().equals(realm.getId())) {
                    // Delete all masterAdmin apps due to foreign key constraints
                    for (RealmModel currRealm : model.getRealms()) {
                        currRealm.setMasterAdminClient(null);
                    }
                }
                model.removeRealm(realm.getId());
            }
        }

        RealmManager realmManager = new RealmManager(session);
//
//        // Cache required actions information and remove it from user representations..
//        // Original version from RepresentationToModel first convert it to an enum
//        // then get the name of the enum value. This fails for customized required actions
//        Map<UserRepresentation, List<String>> mapUserToRequiredActions = new HashMap<>();
//        if (rep.getUsers() != null) {
//            for (UserRepresentation user : rep.getUsers()) {
//                if (user.getRequiredActions() != null) {
//                    mapUserToRequiredActions.put(user, user.getRequiredActions());
//                    user.setRequiredActions(Collections.emptyList());
//                }
//            }
//        }

        // Basic import
        realm = realmManager.importRealm(rep, skipUserDependent);

//        // Now set required actions
//        for (Map.Entry<UserRepresentation, List<String>> entry : mapUserToRequiredActions.entrySet()) {
//            UserRepresentation userRep = entry.getKey();
//            UserModel user = session.userLocalStorage().getUserById(userRep.getId(), realm);
//            entry.getValue().forEach(user::addRequiredAction);
//        }

        return realm;
    }
}
