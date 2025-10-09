import boto3
import hvac
import os

# AWS authentication using OIDC (assumes environment is already configured via GitHub Actions OIDC)
session = boto3.Session()
secrets_client = session.client('secretsmanager')

# HashiCorp Vault authentication using OIDC (assumes VAULT_ADDR is set and OIDC is configured)
# VAULT_ADDR = os.environ['VAULT_ADDR']
# VAULT_ROLE = os.environ['VAULT_ROLE']  # e.g., 'github'
# VAULT_OIDC_JWT = os.environ['VAULT_OIDC_JWT']  # JWT token from GitHub OIDC

# vault_client = hvac.Client(url=VAULT_ADDR)
# vault_client.auth.jwt.jwt_login(
#     role=VAULT_ROLE,
#     jwt=VAULT_OIDC_JWT
# )

def list_aws_secrets():
    secrets = []
    paginator = secrets_client.get_paginator('list_secrets')
    for page in paginator.paginate():
        for secret in page['SecretList']:
            secrets.append(secret['Name'])
    return secrets

def get_aws_secret_value(secret_name):
    response = secrets_client.get_secret_value(SecretId=secret_name)
    return response.get('SecretString', '')

def write_to_vault(secret_name, secret_value, mount_point='secret'):
    path = f"{mount_point}/data/{secret_name}"
    vault_client.secrets.kv.v2.create_or_update_secret(
        path=secret_name,
        secret={'value': secret_value},
        mount_point=mount_point
    )

def migrate_secrets():
    secret_names = list_aws_secrets()
    for name in secret_names:
        print(f"Migrating: {name}")
        value = get_aws_secret_value(name)
        print(f"Value is: {value}")
        #write_to_vault(name, value)

if __name__ == "__main__":
    print("Starting migration...")
    migrate_secrets()
    print("Migration completed.")