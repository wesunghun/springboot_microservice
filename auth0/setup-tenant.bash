#!/usr/bin/env bash
# Auth0 테넌트 준비: product API + writer/reader 클라이언트 + 테스트 사용자
set -e
cd "$(dirname "$0")"
source ./env.bash                     # TENANT, MGM_CLIENT_ID/SECRET, USER_EMAIL/PASSWORD

API_ID="https://localhost:8443/product-composite"
CALLBACKS='["https://my.redirect.uri","https://localhost:8443/openapi/swagger-ui/oauth2-redirect.html"]'

# 1) 관리 API용 토큰 (client credentials)
MGM_TOKEN=$(curl -s --request POST "https://$TENANT/oauth/token" \
  -H 'content-type: application/json' \
  -d "{\"client_id\":\"$MGM_CLIENT_ID\",\"client_secret\":\"$MGM_CLIENT_SECRET\",\"audience\":\"https://$TENANT/api/v2/\",\"grant_type\":\"client_credentials\"}" \
  | jq -r .access_token)
[ "$MGM_TOKEN" = "null" ] && { echo "관리 토큰 실패: env.bash 값 확인"; exit 1; }
AUTH=(-H "Authorization: Bearer $MGM_TOKEN" -H 'content-type: application/json')

# 2) 보호할 API(resource server)와 스코프
curl -s "${AUTH[@]}" -X POST "https://$TENANT/api/v2/resource-servers" -d "{
  \"name\":\"product-composite\",\"identifier\":\"$API_ID\",
  \"scopes\":[{\"value\":\"product:read\",\"description\":\"Read product information\"},
             {\"value\":\"product:write\",\"description\":\"Update product information\"}]}" | jq -r '.identifier // .message'

# 3) 클라이언트 2개 (client credentials + 인가 코드 흐름)
create_client() {   # $1=이름
  curl -s "${AUTH[@]}" -X POST "https://$TENANT/api/v2/clients" -d "{
    \"name\":\"$1\",\"app_type\":\"regular_web\",
    \"grant_types\":[\"client_credentials\",\"authorization_code\",\"refresh_token\"],
    \"callbacks\":$CALLBACKS,\"token_endpoint_auth_method\":\"client_secret_basic\"}"
}
WRITER=$(create_client writer); READER=$(create_client reader)
WRITER_ID=$(echo "$WRITER" | jq -r .client_id); WRITER_SECRET=$(echo "$WRITER" | jq -r .client_secret)
READER_ID=$(echo "$READER" | jq -r .client_id); READER_SECRET=$(echo "$READER" | jq -r .client_secret)

# 4) 클라이언트별 허용 스코프 (writer: 읽기+쓰기, reader: 읽기)
grant() {   # $1=client_id $2=스코프 JSON 배열
  curl -s "${AUTH[@]}" -X POST "https://$TENANT/api/v2/client-grants" \
    -d "{\"client_id\":\"$1\",\"audience\":\"$API_ID\",\"scope\":$2}" | jq -r '.id // .message'
}
grant "$WRITER_ID" '["product:read","product:write"]'
grant "$READER_ID" '["product:read"]'

# 5) 테스트 사용자 + 테넌트 기본값 (인가 코드 흐름에서 쓸 audience, 로그인 DB)
curl -s "${AUTH[@]}" -X POST "https://$TENANT/api/v2/users" -d "{
  \"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\",
  \"connection\":\"Username-Password-Authentication\",\"email_verified\":true}" | jq -r '.user_id // .message'
curl -s "${AUTH[@]}" -X PATCH "https://$TENANT/api/v2/tenants/settings" \
  -d "{\"default_audience\":\"$API_ID\",\"default_directory\":\"Username-Password-Authentication\"}" | jq -r '.default_audience // .message'

# 6) 다음 단계에서 쓸 값
echo
echo "export TENANT=$TENANT"
echo "export WRITER_CLIENT_ID=$WRITER_ID"
echo "export WRITER_CLIENT_SECRET=$WRITER_SECRET"
echo "export READER_CLIENT_ID=$READER_ID"
echo "export READER_CLIENT_SECRET=$READER_SECRET"