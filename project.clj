(defproject mongo-driver-3 "0.7.0-getaroom-debug"
  :description "A Clojure wrapper for the Java MongoDB driver 3.11/4.0+."
  :url "https://github.com/gnarroway/mongo-driver-3"
  :license {:name         "The MIT License"
            :url          "http://opensource.org/licenses/mit-license.php"
            :distribution :repo}
  :deploy-repositories [["clojars" {:url           "https://clojars.org/repo"
                                    :username      :env/clojars_user
                                    :password      :env/clojars_pass
                                    :sign-releases false}]]
  :plugins [[lein-cljfmt "0.6.4"]]

  :dependencies [[clj-time "0.15.0" :exclusions [joda-time]]]

  :profiles {:dev {:dependencies [[org.clojure/clojure "1.11.1"]
                                  ;;[org.mongodb/mongodb-driver-sync "4.7.1"]

                                  ;; https://mvnrepository.com/artifact/org.xerial.snappy/snappy-java
                                  [org.xerial.snappy/snappy-java "1.1.8.4"]


                                  ;; temporary benchmark
                                  [criterium "0.4.6"]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.mongodb/mongodb-driver-sync "3.12.11"]
                                  [org.mongodb/mongodb-driver "3.12.11"]
                                  [com.novemberain/monger "3.5.0"]
                                  [joda-time "2.10.2"]]}})
