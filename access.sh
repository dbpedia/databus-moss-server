TOKEN_RESPONSE=$(curl -s -X POST https://auth.dbpedia.org/realms/dbpedia/protocol/openid-connect/token \
	-H "Content-Type: application/x-www-form-urlencoded" \
	-d "grant_type=password" \
	-d "client_id=splt" \
	-d "username=s" \
	-d "password=test" \
	-d "scope=openid")
echo $TOKEN_RESPONSE
echo "******"
ACCESS_TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.access_token')
echo $ACCESS_TOKEN