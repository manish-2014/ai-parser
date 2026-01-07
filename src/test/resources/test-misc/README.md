# hub

FIXME

## Prerequisites

You will need [Leiningen][1] 1.7.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein with-profile localqa1 ring server
    or ./start.sh 

    

 ### Runnig Prerequisite:
1. run.env must be set appropriately
   file_path=/home/manish/projects/devops/environment-center/localqa.videocloudmanager.com/fdbrecords/run.env
  this file sets the environment variables
   FDB_LIB_PATH= path to libfdb_c.x86_64.so for foundationdb 7.4
   FDB_CLUSTER_FILE_PATH = path to fdb.cluster file
2. profiles.clj must be set appropriately
   


## License

## Static HTML and javascripts

The source for html and javascrpt is **src/web**
### Building HTML templates and sending JS to web server
    

#### How to build/publish static files
    **gulp build**

     **pre build for jeknins**
     ***src*** https://stackoverflow.com/questions/46870020/cache-npm-dependencies-on-jenkins-pipeline
     1. goto to vcmweb
     2. tar the files ( command in the readme.md of vcmweb)
     3. ensure that the tarfile is in **/home/manish/projects/jenkins-environment/docker-volumes/node-1/acache**
     4. ensure docker compose for agent has this volume  - /home/manish/projects/jenkins-environment/docker-volumes/node-1/acache:/home/jenkins/acache




# REPL

1. to start repl ./repl.sh
2. use calva to connect
3. rename src/newzing_config/replr.clj.txt to src/newzing_config/replr.clj
4. using reple: set up namespaces
         (use 'hub.init)
         (init)
         (use ' [newzing-config.cloudmanager :as cloud-manager]) 
          (use '[newzing-config.core :refer [get-assets-url app-config]])
5. check config
   
   (-> @app-config :vcm-env )
 now you can use the cloud-manager functions  

# Environment

1. to start environment ./start.sh
2. the aws environment is loaded from profile.clj
3. the local environment for my testing is using aws account 875678036834. 
4. The env path is loaded from AWSSSM parameter store. /vcm/sandbox/localqa1.videocloudmanager.com/env

## Environment : Bad stuff
**The newzing-config.cloudmanager is hardcoded with cloud information. This needs to be fixed.**
### Environment : Good stuff todo
move the enviroment to devops folder and load it from there 
or make something to load it from the Kubernetes config map

#  How it works : 
## Video Posted videohandler
1. when new video is posted, it uploads the video to s3 bucket
2. it creates a file in S3 bucket with VIDEOJOB doc (cloud-manager/get-job-input-cloud-bucket-info)
3. it sends a message to SQS queue with the videojob doc (send-sqs-message (-> @app-config :vcm-env :VIDEO_SQS_IN) (cheshire/generate-string vjob)  )
4. we are suppressing the ecs batch job for now

## Video job success notification
1. when the video job is done, vidoe job processor sends a message to SQS queue **videojobs-out-us-east-1-sandboxes-managedvideocloud-com**
2. the message is picked up by the videojobhandler
3. videojobhandler updates the database with the videojob doc


## Testing Video Posted
1. We use /clojure-dev/videoprocessor for processing videos
2. ideally we shoud be using 

# Pagination
Use paginator.mvc for pagination. check out the code in encododing jobs list for example