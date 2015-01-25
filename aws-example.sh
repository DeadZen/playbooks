
# =============================================================================================================
# Modify the variables below accorind to your AWS account and save this file as "aws.sh" in the same directory
# =============================================================================================================

export AWS_DEFAULT_REGION="eu-west-1"
export AWS_KEYPAIR_NAME="..."
export AWS_SSH_PRIVATE_KEY="..."
export AWS_SUBNET_ID="..."
export AWS_ACCESS_KEY_ID="..."
export AWS_SECRET_ACCESS_KEY="..."

# =============================================================================================================
# Only modify the variables below if you know what you're doing
# =============================================================================================================

export AWS_AMI="ami-f0b11187"
export AWS_INSTANCE_TYPE="c4.2xlarge"
export AWS_EBS_SIZE="20"
export AWS_SSH_USER="ubuntu"

export REPO_PORT="8080"
export REPO_JAVA_OPTIONS="-server -Xms512m -Xmx14000m"
export REPO_IMPORT="https://s3-eu-west-1.amazonaws.com/evgenyg-ansible/m2-import.zip"