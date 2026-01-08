#!/bin/bash
set -a # Automatically export variables
  # Replace with your .env file path

file_path="/home/manish/projects/devops/environment-center/APIKeysForLLMs/.env"

# Read each line in the file and export the key-value pair as an environment variable
while IFS='=' read -r key value; do
  # Export the key-value pair as an environment variable
  export "$key"="$value"
  echo "$key = $value"
done < "$file_path"
# Ensure log folder exists so Log4j2 can write there.
mkdir -p logs
#mvn -Dtest=DeepSeekSummarizerTest test
#mvn -Dtest=HaikuSummarizerTest test
mvn clean compile test
