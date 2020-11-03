package com.charlyghislain.keycloak.importexport;

import org.jboss.resteasy.annotations.Query;
import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.common.ClientConnection;
import org.keycloak.credential.CredentialModel;
import org.keycloak.exportimport.util.ExportUtils;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.models.AdminRoles;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakUriInfo;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.policy.PasswordPolicyNotMetException;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
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
import org.keycloak.urls.UrlType;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
    public RealmRepresentation exportRealm(@Context final HttpHeaders headers, @Context final UriInfo uriInfo,
                                           @QueryParam("users") Boolean includeUsers) {
        // This fixes double slashes
        UriInfo keycloakUriInfo = new KeycloakUriInfo(session, UrlType.BACKEND, uriInfo);
        //retrieving the realm should be done before authentication
        // authentication overrides the value with master inside the context
        // this is done this way to avoid changing the copied code below (authenticateRealmAdminRequest)
        RealmModel realm = session.getContext().getRealm();
        AdminAuth adminAuth = authenticateRealmAdminRequest(headers, keycloakUriInfo);
        RealmManager realmManager = new RealmManager(session);
        RoleModel roleModel = adminAuth.getRealm().getRole(AdminRoles.ADMIN);
        AdminPermissionEvaluator realmAuth = AdminPermissions.evaluator(session, realm, adminAuth);
        if (roleModel != null && adminAuth.getUser().hasRole(roleModel)
                && adminAuth.getRealm().equals(realmManager.getKeycloakAdminstrationRealm())
                && realmAuth.realm().canManageRealm()) {
            boolean includeUsersValue = Optional.ofNullable(includeUsers)
                    .orElse(false);
            RealmRepresentation realmRep = ExportUtils.exportRealm(session, realm, includeUsersValue, true);
            //correct users
            if (realmRep.getUsers() != null) {
                setCorrectCredentials(realmRep.getUsers(), realm);
            }
            return realmRep;
        } else {
            throw new ForbiddenException();
        }
    }

    /**
     * This method rewrites the credential list for the users, including the Id (which is missing by default).
     * Unfortunately, due to the limitations in the keycloak API, there is no way to unit test this.
     *
     * @param users The user representations to correct
     * @param realm the realm being exported
     */
    private void setCorrectCredentials(List<UserRepresentation> users, RealmModel realm) {
        Map<String, UserRepresentation> userRepMap = new HashMap<>(users.size());
        for (UserRepresentation userRep : users) {
            userRepMap.put(userRep.getId(), userRep);
        }

        for (UserModel user : session.users().getUsers(realm, true)) {
            UserRepresentation userRep = userRepMap.get(user.getId());
            if (userRep != null) {
                // Credentials
                List<CredentialModel> creds = session.userCredentialManager().getStoredCredentials(realm, user);
                List<CredentialRepresentation> credReps = creds.stream().map(this::exportCredential).collect(Collectors.toList());
                userRep.setCredentials(credReps);
            }
        }
    }

    private CredentialRepresentation exportCredential(CredentialModel userCred) {
        CredentialRepresentation credRep = new CredentialRepresentation();
        credRep.setId(userCred.getId());
        credRep.setType(userCred.getType());
        credRep.setCreatedDate(userCred.getCreatedDate());
        credRep.setCredentialData(userCred.getCredentialData());
        credRep.setSecretData(userCred.getSecretData());
        credRep.setUserLabel(userCred.getUserLabel());
        return credRep;
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
    private AdminAuth authenticateRealmAdminRequest(HttpHeaders headers, UriInfo uriInfo) {
        String tokenString = authManager.extractAuthorizationHeaderToken(headers);
        if (tokenString == null) throw new NotAuthorizedException("Bearer");
        AccessToken token;
        try {
            JWSInput input = new JWSInput(tokenString);
            token = input.readJsonContent(AccessToken.class);
        } catch (JWSInputException e) {
            throw new NotAuthorizedException("Bearer token format error", e);
        }
        String realmName = token.getIssuer().substring(token.getIssuer().lastIndexOf('/') + 1);
        RealmManager realmManager = new RealmManager(session);
        RealmModel realm = realmManager.getRealmByName(realmName);
        if (realm == null) {
            throw new NotAuthorizedException("Unknown realm in token");
        }
        session.getContext().setRealm(realm);
        AuthenticationManager.AuthResult authResult = authManager.authenticateBearerToken(session, realm, uriInfo, clientConnection, headers);
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
    public Response importRealm(@Context final HttpHeaders headers, @Context final UriInfo uriInfo, @Context KeycloakApplication keycloak,
                                RealmRepresentation rep) {
        try {
            AdminAuth auth = authenticateRealmAdminRequest(headers, uriInfo);
            AdminPermissions.realms(session, auth).requireCreateRealm();

            RealmModel realm = ImportExportUtils.importRealm(session, keycloak, rep, null, false);
            grantPermissionsToRealmCreator(auth, realm);

            URI location = AdminRoot.realmsUrl(session.getContext().getUri()).path(realm.getName()).build();
            logger.log(Level.FINE, "imported realm success, sending back: {0}", location);

            return Response.created(location).build();
        } catch (ModelDuplicateException e) {
            logger.log(Level.SEVERE, "Conflict detected", e);
            return ErrorResponse.exists("Conflict detected. See logs for details");
        } catch (PasswordPolicyNotMetException e) {
            logger.log(Level.SEVERE, "Password policy not met for user " + e.getUsername(), e);
            if (session.getTransactionManager().isActive()) session.getTransactionManager().setRollbackOnly();
            return ErrorResponse.error("Password policy not met. See logs for details", Response.Status.BAD_REQUEST);
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

