# Keycloak importexport

Creates a rest resource to export realms.

Stole from https://github.com/cloudtrust/keycloak-export.


## Getting started

- Deploy the ear (com.charlyghislain.keycloak:keycloak-importexport-ear:ear)
  into your keycloak installation (/opt/jboss/keycloak/standalone/deployments).

- Obtain a token for the admin realm:
```
 curl -k  -d "client_id=admin-cli" \
   -d "username=admin" \
   -d "password=XXXX" \
   -d "grant_type=password" \
   "<keycloak-url>/realms/master/protocol/openid-connect/token" | jq '.access_token'
```


- Export any realm using that token for authentication:
```
curl -k -H 'accept: application/json' \
 -H 'authorization: bearer <access_token>' \
 "<keycloak-url>/realms/<realm>/importexport/realm" 
```