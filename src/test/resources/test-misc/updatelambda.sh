#!/bin/bash

set -x
set -e

aws --profile manishadmin lambda update-function-code \
    --function-name  VCMImgUtil \
    --s3-key image-function.zip  \
    --s3-bucket lambda.east1.managedvideocloud.com
    
