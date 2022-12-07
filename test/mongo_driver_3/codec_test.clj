(ns mongo-driver-3.codec-test
  (:require [clojure.test :refer :all]
            [mongo-driver-3.codec :as c]
            [mongo-driver-3.model :as m])
  (:import (org.bson.codecs Codec EncoderContext DecoderContext)
           (org.bson.codecs.configuration CodecRegistry)
           (org.bson Document BsonDocumentWriter BsonDocument BsonDocumentReader)))


(def default-registry (c/clojure-registry c/bson-types))


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
   (decode x clojure.lang.PersistentArrayMap default-registry))
  ([^BsonDocument x t]
   (decode x t default-registry))
  ([^BsonDocument x t ^CodecRegistry registry]
   (let [ctx    (.build (DecoderContext/builder))
         codec  (.get registry t)
         reader (BsonDocumentReader. x)]
     (.decodeWithChildContext ctx codec reader))))


(defn roundtrip [value]
  (-> value
      encode
      decode
      :root))


(deftest test-round-trip
  (testing "vector"
    (is (= clojure.lang.PersistentVector (type (roundtrip []))))
    (is (= [] (roundtrip [])))
    (is (= [nil] (roundtrip [nil])))))


(deftest test-vector-encoding
  (let []
    ))


(comment

  (require '[mongo-driver-3.client :as client])
  (require '[mongo-driver-3.collection :as mc])
  (def client (client/create))
  (def db (client/get-db client "availability_preprod"))
  )
