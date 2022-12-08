(ns mongo-driver-3.codec-test
  (:require [clojure.test :refer :all]
            [mongo-driver-3.codec :as c]
            [mongo-driver-3.model :as m]
            [clojure.walk :as walk])
  (:import (org.bson.codecs Codec EncoderContext DecoderContext)
           (org.bson.codecs.configuration CodecRegistry)
           (org.bson BsonType Document BsonDocumentWriter BsonDocument BsonDocumentReader)))


(def default-registry (c/clojure-registry))


(defn ^BsonDocument encode
  "Encode a value according to registry"
  ([x] (encode x default-registry))
  ([x ^CodecRegistry registry]
   (let [ctx    (.build (EncoderContext/builder))
         codec  (.get registry (type x))
         root   (BsonDocument.)
         writer (doto (BsonDocumentWriter. root)
                  (.writeStartDocument)
                  (.writeName "root"))
         _      (.encodeWithChildContext ctx codec writer x)
         _      (.writeEndDocument writer)]
     root)))


(defn decode
  "Decodes x according to registry.  Assumes a BSON document with field root."
  ([^BsonDocument x]
   (decode x default-registry))
  ([^BsonDocument x ^CodecRegistry registry]
   (let [ctx    (.build (DecoderContext/builder))
         codec  (.get registry clojure.lang.PersistentArrayMap)
         reader (BsonDocumentReader. x)]
     (.decodeWithChildContext ctx codec reader))))


(defn roundtrip
  "Encodes & decodes value according to registry"
  ([value]
   (roundtrip value default-registry))
  ([value registry]
   (-> value
       (encode registry)
       (decode registry)
       vals
       first)))


(defn types [x]
  (walk/postwalk #(if (instance? clojure.lang.MapEntry %) % [(type %) %]) x))


(deftest test-round-trip
  (testing "vector"
    (is (= clojure.lang.PersistentVector (type (roundtrip []))))
    (is (= [] (roundtrip [])))
    (is (= [[]] (roundtrip [[]])))
    (is (= [clojure.lang.PersistentVector [[clojure.lang.PersistentVector []]]] (types (roundtrip [[]])))))
  (testing "map"
    (is (= {"k" "v"} (roundtrip (hash-map "k" "v") (c/clojure-registry c/default-bson-types {:keyword? false}))))
    (is (= {:k "v"} (roundtrip (hash-map "k" "v") (c/clojure-registry c/default-bson-types {:keyword? true}))))
    (is (= {:k "v"} (roundtrip (hash-map "k" "v"))))
    (is (= {:k "v"} (roundtrip (array-map "k" "v"))))
    (is (= {} (roundtrip (array-map))))
    (is (= {:x nil} (roundtrip {:x nil})))
    (is (= [clojure.lang.PersistentArrayMap {[clojure.lang.Keyword :k] [clojure.lang.PersistentArrayMap {}]}]
           (types (roundtrip (hash-map :k (array-map)))))))
  (testing "keyword"
    (is (= "keyword" (roundtrip :keyword)))
    (is (thrown? java.lang.AssertionError (roundtrip :keyword (c/clojure-registry (assoc c/default-bson-types BsonType/STRING clojure.lang.Keyword)))))))


(deftest test-encoding
  )


(deftest test-decoding
  )
