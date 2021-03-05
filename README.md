# Keycloak importexport

Creates a rest resource to export realms.

Stolen from https://github.com/cloudtrust/keycloak-export.


## Getting started

- Deploy the ear (com.charlyghislain.keycloak:keycloak-importexport-ear:ear, see https://repo1.maven.org/maven2/com/charlyghislain/keycloak/keycloak-importexport-ear/)
  into your keycloak installation (/opt/jboss/keycloak/standalone/deployments).

- Obtain an admin token for the master realm:
```
 curl -k  -d "client_id=admin-cli" \
   -d "username=admin" \
   -d "password=XXXX" \
   -d "grant_type=password" \
   "<keycloak-url>/realms/master/protocol/openid-connect/token" | jq '.access_token'
```


- Export any realm using that token:
```
curl -k -H 'accept: application/json' \
 -H 'authorization: bearer <access_token>' \
 -H 'accept: application/json' \
 "<keycloak-url>/realms/<realm>/importexport/realm" 
```

- To include users, pass the query param `?users=true`

## Troubleshouting

- If you use another url than the public one, you may need to override the
  `Host` header in order for keycloak to match its token `issuer`.