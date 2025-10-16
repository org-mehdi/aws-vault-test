vault auth enable jwt

vault write auth/jwt/config \
    oidc_discovery_url="https://token.actions.githubusercontent.com" \
    bound_issuer="https://token.actions.githubusercontent.com"

vault write auth/jwt/role/github-actions \
    role_type="jwt" \
    bound_audiences="https://github.com/org-mehdi" \
    bound_subject="repo:org-mehdi/*" \
    user_claim="actor" \
    policies="secret-writer" \
    ttl=1h