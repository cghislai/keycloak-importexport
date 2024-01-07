package com.charlyghislain.keycloak.importexport;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.common.ClientConnection;
import org.keycloak.exportimport.Strategy;
import org.keycloak.exportimport.util.ExportUtils;
import org.keycloak.http.HttpRequest;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.models.*;
import org.keycloak.policy.PasswordPolicyNotMetException;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.ErrorResponse;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resources.Cors;
import org.keycloak.services.resources.KeycloakApplication;
import org.keycloak.services.resources.admin.AdminAuth;
import org.keycloak.services.resources.admin.AdminRoot;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;
import org.keycloak.services.resources.admin.permissions.AdminPermissions;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KeycloakImportExportRestResourceProvider implements RealmResourceProvider {

    protected static final Logger logger = Logger.getLogger(KeycloakImportExportRestResourceProvider.class.getName());

    private KeycloakSession session;

    protected AppAuthManager authManager;

    @Context
    protected ClientConnection clientConnection;

    @Context
    private HttpRequest request;

    public KeycloakImportExportRestResourceProvider(KeycloakSession session) {
        this.session = session;
        this.authManager = new AppAuthManager();

    }

    @Override
    public Object getResource() {
        return this;
    }

    @OPTIONS
    @Path("realm")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getVersionPreflight() {
        return Cors.add(request, Response.ok()).allowedMethods("GET").preflight().auth().build();
    }

    @GET
    @Path("realm")
    @Produces(MediaType.APPLICATION_JSON)
    public RealmRepresentation exportRealm(@Context final HttpHeaders headers,
                                           @QueryParam("users") Boolean includeUsers) {
        RealmModel realm = session.getContext().getRealm();
        AdminAuth adminAuth = authenticateRealmAdminRequest(headers);
        RealmManager realmManager = new RealmManager(session);
        RoleModel roleModel = adminAuth.getRealm().getRole(AdminRoles.ADMIN);
        AdminPermissionEvaluator realmAuth = AdminPermissions.evaluator(session, realm, adminAuth);
        if (roleModel != null && adminAuth.getUser().hasRole(roleModel)
                && adminAuth.getRealm().equals(realmManager.getKeycloakAdminstrationRealm())
                && realmAuth.realm().canManageRealm()) {
            boolean includeUsersValue = Optional.ofNullable(includeUsers)
                    .orElse(false);
            RealmRepresentation realmRep = ExportUtils.exportRealm(session, realm, includeUsersValue, true);
            return realmRep;
        } else {
            throw new ForbiddenException();
        }
    }


    @Override
    public void close() {
        // Nothing to close
    }

    /**
     * This code has been copied from keycloak org.keycloak.services.resources.admin.AdminRoot;
     * it allows to check if a user as realm/master admin
     * at each upgrade check that it hasn't been modified
     */
    protected AdminAuth authenticateRealmAdminRequest(HttpHeaders headers) {
        String tokenString = AppAuthManager.extractAuthorizationHeaderToken(headers);
        if (tokenString == null) throw new NotAuthorizedException("Bearer");
        AccessToken token;
        try {
            JWSInput input = new JWSInput(tokenString);
            token = input.readJsonContent(AccessToken.class);
        } catch (JWSInputException e) {
            throw new NotAuthorizedException("Bearer token format error");
        }
        String realmName = token.getIssuer().substring(token.getIssuer().lastIndexOf('/') + 1);
        RealmManager realmManager = new RealmManager(session);
        RealmModel realm = realmManager.getRealmByName(realmName);
        if (realm == null) {
            throw new NotAuthorizedException("Unknown realm in token");
        }
        session.getContext().setRealm(realm);

        AuthenticationManager.AuthResult authResult = new AppAuthManager.BearerTokenAuthenticator(session)
                .setRealm(realm)
                .setConnection(clientConnection)
                .setHeaders(headers)
                .authenticate();

        if (authResult == null) {
            logger.fine("Token not valid");
            throw new NotAuthorizedException("Bearer");
        }

        ClientModel client = realm.getClientByClientId(token.getIssuedFor());
        if (client == null) {
            throw new NotFoundException("Could not find client for authorization");

        }

        return new AdminAuth(realm, authResult.getToken(), authResult.getUser(), client);
    }

    @POST
    @Path("realm")
    @Produces(MediaType.APPLICATION_JSON)
    public Response importRealm(@Context final HttpHeaders headers, @Context KeycloakApplication keycloak,
                                @QueryParam("strategy") String strategyParam,
                                @QueryParam("skipUserDependant") Boolean skipUserDependantparam,
                                RealmRepresentation rep) {
        try {
            Strategy overwriteStragegy = Optional.ofNullable(strategyParam)
                    .flatMap(strategy -> Arrays.stream(Strategy.values())
                            .filter(s -> s.name().equalsIgnoreCase(strategy))
                            .findAny())
                    .orElse(Strategy.OVERWRITE_EXISTING);
            boolean skipUsersDependants = Optional.ofNullable(skipUserDependantparam)
                    .orElse(false);

            AdminAuth auth = authenticateRealmAdminRequest(headers);
            AdminPermissions.realms(session, auth).requireCreateRealm();

            RealmModel realm = ImportExportUtils.importRealm(session, keycloak, rep, overwriteStragegy, skipUsersDependants);
            grantPermissionsToRealmCreator(auth, realm);

            URI location = AdminRoot.realmsUrl(session.getContext().getUri()).path(realm.getName()).build();
            logger.log(Level.FINE, "imported realm success, sending back: {0}", location);

            return Response.created(location).build();
        } catch (ModelDuplicateException e) {
            logger.log(Level.SEVERE, "Conflict detected", e);
            throw ErrorResponse.exists("Conflict detected. See logs for details");
        } catch (PasswordPolicyNotMetException e) {
            logger.log(Level.SEVERE, "Password policy not met for user " + e.getUsername(), e);
            if (session.getTransactionManager().isActive()) session.getTransactionManager().setRollbackOnly();
            throw ErrorResponse.error("Password policy not met. See logs for details", Response.Status.BAD_REQUEST);
        }
    }

    private void grantPermissionsToRealmCreator(AdminAuth auth, RealmModel realm) {
        if (auth.hasRealmRole(AdminRoles.ADMIN)) {
            return;
        }

        new RealmManager(session).getKeycloakAdminstrationRealm();
        ClientModel realmAdminApp = realm.getMasterAdminClient();
        for (String r : AdminRoles.ALL_REALM_ROLES) {
            RoleModel role = realmAdminApp.getRole(r);
            auth.getUser().grantRole(role);
        }
    }
}

