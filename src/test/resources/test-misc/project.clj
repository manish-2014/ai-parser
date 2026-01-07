(defproject com.videocloudmanager/hub "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.12.0-alpha2"]
                 [javax.servlet/servlet-api "2.5"]
                 [ring "1.9.4" :exclusions [joda-time/joda-time]]
                 [ring/ring-json "0.5.0"]
                 ;[ring/ring-jetty-adapter "1.8.1"]

                 [compojure "1.6.1"]
                 [cheshire "5.11.0"]
                 [liberator "0.15.3"]
                 [propertea "1.2.3"]
                 [clj-http "3.10.0"]
                 [me.shenfeng/mustache "1.1"]

                 [com.videocloudmanager/topmenu "0.1.0-SNAPSHOT"]
                 [joda-time "2.10"]
                 [clj-time "0.15.0"]
                 [clojurewerkz/money "1.10.0"]
                 [com.videocloudmanager/newzing-config "0.1.0-SNAPSHOT"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [com.newzing/geotools "0.1.0-SNAPSHOT"]
    ;[com.videocloudmanager/datauttils "0.1.0-SNAPSHOT"]
                 [com.videocloudmanager/newzing-connections "0.1.0-SNAPSHOT"]

                 [hiccup "1.0.5"]
                 [me.shenfeng/mustache "1.1"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [com.taoensso/nippy "3.0.0"]
                 [com.github.davidmoten/geo "0.6.7"]
                 [com.newzing/geotools "0.1.0-SNAPSHOT"]
                 [com.videocloudmanager/stripeutil "0.1.0-SNAPSHOT"]

                 [org.clojure/tools.logging "1.2.4"]
                 [org.apache.logging.log4j/log4j-api "2.18.0"]
                 [org.apache.logging.log4j/log4j-core "2.18.0"]
                 [org.apache.logging.log4j/log4j-1.2-api "2.18.0"]
                 [org.apache.logging.log4j/log4j-jcl "2.18.0"]
                 [org.apache.logging.log4j/log4j-jul "2.18.0"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.18.0"]

                 [org.clojure/tools.trace "0.7.10"]
                 [com.videocloudmanager/commonservices "0.1.0-SNAPSHOT"]
                 [jstrutz/hashids "1.0.1"]
                 [org.ocpsoft.prettytime/prettytime "3.2.5.Final"]

                 [com.fasterxml.jackson.core/jackson-core "2.12.4"]
                 [com.fasterxml.jackson.core/jackson-databind "2.12.4"]
                 [com.fasterxml.jackson.core/jackson-annotations "2.12.4"]
                 [ vcm-fdb-dao "0.1.0-SNAPSHOT" :exclusions [org.mongodb/bson]]
                 [org.petikaa/petikaa "0.1.0-SNAPSHOT"]
                 [com.videocloudmanager/vcm-petikaa "0.1.0-SNAPSHOT"]
                 [com.videocloudmanager.dbtier/fdbrecords "1.0-SNAPSHOT" :exclusions [org.mongodb/bson]]
                 [org.foundationdb/fdb-record-layer-core "3.3.397.0" ]
                 [com.appsflyer/pronto "2.1.2"]



                 [com.videocloudmanager.model/vcm-beans "1.0-SNAPSHOT"]
                 [com.managedvideocloud.aws/managedvideocloud-awstasks "1.0-SNAPSHOT"]
                 [danlentz/clj-uuid "0.1.7"]
                 [me.raynes/fs "1.4.6"]

                 [org.clojure/data.json  "2.4.0"]
                 [org.clojure/data.codec "0.1.1"]
                 [com.cognitect.aws/api    "0.8.641"]
                 [com.cognitect.aws/endpoints "1.1.12.398"]
                 [com.cognitect.aws/s3        "825.2.1250.0"]
                 [com.cognitect.aws/sqs        "822.2.1109.0"]

                 [javax.xml.bind/jaxb-api "2.3.1"]

                 ;; JAXB Runtime (Glassfish implementation)
                 [org.glassfish.jaxb/jaxb-runtime "2.3.1"]

                 ;; Activation Framework (required by JAXB)
                 [javax.activation/javax.activation-api "1.2.0"]
                 ]
   


  ;[lein-exec "0.3.7"]
  :source-paths ["src/clojure"]
  :repositories [["FDB Record Layer" "https://ossartifacts.jfrog.io/artifactory/fdb-record-layer"]]

  :uberjar-name "hub.jar"
  :plugins [[lein-ring "0.12.5"] [lein-environ "1.1.0"]  [lein-exec "0.3.7"] [versioner "0.1.0"]
            [jonase/eastwood "0.3.5"]
            [lein-cljfmt "0.7.0"]]


  :ring {:uberwar-name hub.war

         :init  hub.init/init

         :destroy newzing-connections.core/destroy
         :handler hub.routes/app

         :auto-reload? true
       ;:auto-refresh? true
         :reload-paths ["src"]

         :port 3008})





;(use 'hub.biz)
; (refresh-pages)
;(use 'newzing-connections.core) (prod-init)
