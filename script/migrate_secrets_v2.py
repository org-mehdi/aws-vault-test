import os
import sys
import json
import boto3
import requests
from typing import Dict, List, Optional
from botocore.exceptions import ClientError

class SecretsManager:
    def __init__(self):
        # AWS credentials come from OIDC/GitHub Actions environment
        self.aws_region = os.getenv('AWS_REGION', 'us-east-1')
        self.secrets_client = boto3.client('secretsmanager', region_name=self.aws_region)
        
    def list_secrets(self, prefix: Optional[str] = None) -> List[Dict]:
        """List all secrets from AWS Secrets Manager"""
        secrets = []
        paginator = self.secrets_client.get_paginator('list_secrets')
        
        try:
            for page in paginator.paginate():
                for secret in page['SecretList']:
                    if prefix and not secret['Name'].startswith(prefix):
                        continue
                    secrets.append(secret)
            print(f"Found {len(secrets)} secrets in AWS Secrets Manager")
            return secrets
        except ClientError as e:
            print(f"Error listing secrets: {e}")
            sys.exit(1)
    
    def get_secret_value(self, secret_name: str) -> Dict:
        """Retrieve secret value from AWS Secrets Manager"""
        try:
            response = self.secrets_client.get_secret_value(SecretId=secret_name)
            
            if 'SecretString' in response:
                secret_data = response['SecretString']
                try:
                    return json.loads(secret_data)
                except json.JSONDecodeError:
                    return {'value': secret_data}
            else:
                return {'binary': response['SecretBinary']}
                
        except ClientError as e:
            print(f"Error retrieving secret {secret_name}: {e}")
            return None

class VaultClient:
    def __init__(self):
        self.vault_addr = os.getenv('VAULT_ADDR')
        self.vault_namespace = os.getenv('VAULT_NAMESPACE', '')
        self.jwt_token = os.getenv('ACTIONS_ID_TOKEN_REQUEST_TOKEN')
        self.jwt_url = os.getenv('ACTIONS_ID_TOKEN_REQUEST_URL')
        self.vault_role = os.getenv('VAULT_ROLE')
        self.vault_token = None
        
        if not self.vault_addr:
            print("Error: VAULT_ADDR environment variable is required")
            sys.exit(1)
        
        if not self.vault_role:
            print("Error: VAULT_ROLE environment variable is required")
            sys.exit(1)
    
    def get_github_oidc_token(self) -> str:
        """Retrieve GitHub OIDC token"""
        if not self.jwt_token or not self.jwt_url:
            print("Error: GitHub OIDC token not available. Ensure id-token: write permission is set")
            sys.exit(1)
        
        try:
            headers = {'Authorization': f'Bearer {self.jwt_token}'}
            response = requests.get(self.jwt_url, headers=headers)
            response.raise_for_status()
            return response.json()['value']
        except Exception as e:
            print(f"Error retrieving GitHub OIDC token: {e}")
            sys.exit(1)
    
    def authenticate(self):
        """Authenticate to Vault using GitHub OIDC"""
        jwt = self.get_github_oidc_token()
        
        headers = {}
        if self.vault_namespace:
            headers['X-Vault-Namespace'] = self.vault_namespace
        
        auth_url = f"{self.vault_addr}/v1/auth/jwt/login"
        payload = {
            'role': self.vault_role,
            'jwt': jwt
        }
        
        try:
            response = requests.post(auth_url, json=payload, headers=headers)
            response.raise_for_status()
            self.vault_token = response.json()['auth']['client_token']
            print(f"Successfully authenticated to Vault (namespace: {self.vault_namespace or 'root'})")
        except Exception as e:
            print(f"Error authenticating to Vault: {e}")
            if hasattr(e, 'response') and hasattr(e.response, 'text'):
                print(f"Response: {e.response.text}")
            sys.exit(1)
    
    def write_secret(self, path: str, data: Dict) -> bool:
        """Write secret to Vault KV v2"""
        if not self.vault_token:
            print("Error: Not authenticated to Vault")
            return False
        
        headers = {
            'X-Vault-Token': self.vault_token,
        }
        
        if self.vault_namespace:
            headers['X-Vault-Namespace'] = self.vault_namespace
        
        # Assuming KV v2 engine mounted at 'secret'
        kv_mount = os.getenv('VAULT_KV_MOUNT', 'secret')
        url = f"{self.vault_addr}/v1/{kv_mount}/data/{path}"
        
        payload = {'data': data}
        
        try:
            response = requests.post(url, json=payload, headers=headers)
            response.raise_for_status()
            return True
        except Exception as e:
            print(f"Error writing secret to Vault at {path}: {e}")
            if hasattr(e, 'response') and hasattr(e.response, 'text'):
                print(f"Response: {e.response.text}")
            return False

def migrate_secrets():
    """Main migration function"""
    print("Starting secrets migration from AWS Secrets Manager to Vault")
    print("=" * 60)
    
    # Configuration
    aws_secret_prefix = os.getenv('AWS_SECRET_PREFIX', '')
    vault_path_prefix = os.getenv('VAULT_PATH_PREFIX', '')
    dry_run = os.getenv('DRY_RUN', 'false').lower() == 'true'
    
    if dry_run:
        print("Running in DRY RUN mode - no secrets will be written to Vault")
    
    # Initialize clients
    aws_sm = SecretsManager()
    vault = VaultClient()
    
    # Authenticate to Vault
    vault.authenticate()
    
    # Get all secrets from AWS
    secrets = aws_sm.list_secrets(prefix=aws_secret_prefix)
    
    if not secrets:
        print("No secrets found to migrate")
        return
    
    # Migrate each secret
    successful = 0
    failed = 0
    
    for secret in secrets:
        secret_name = secret['Name']
        print(f"\nProcessing: {secret_name}")
        
        # Get secret value
        secret_data = aws_sm.get_secret_value(secret_name)
        
        if not secret_data:
            print(f"  ✗ Failed to retrieve secret data")
            failed += 1
            continue
        
        # Determine Vault path
        vault_path = secret_name
        if aws_secret_prefix and secret_name.startswith(aws_secret_prefix):
            vault_path = secret_name[len(aws_secret_prefix):].lstrip('/')
        
        if vault_path_prefix:
            vault_path = f"{vault_path_prefix}/{vault_path}".strip('/')
        
        print(f"  AWS: {secret_name}")
        print(f"  Vault: {vault_path}")
        
        # Write to Vault
        if dry_run:
            print(f"  ✓ Would write to Vault (dry run)")
            successful += 1
        else:
            if vault.write_secret(vault_path, secret_data):
                print(f"  ✓ Successfully migrated")
                successful += 1
            else:
                print(f"  ✗ Failed to write to Vault")
                failed += 1
    
    # Summary
    print("\n" + "=" * 60)
    print(f"Migration complete:")
    print(f"  Total secrets: {len(secrets)}")
    print(f"  Successful: {successful}")
    print(f"  Failed: {failed}")
    
    if failed > 0:
        sys.exit(1)

if __name__ == "__main__":
    migrate_secrets()