(ns mongo-driver-3.bson
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen])
  (:import (org.bson Document BsonDouble BsonString BsonDocumentReader BsonDocument BsonBoolean BsonNull BsonRegularExpression
                     BsonDateTime BsonTimestamp BsonObjectId BsonArray BsonJavaScript
                     BsonDecimal128 BsonInt32 BsonInt64 BsonMinKey BsonMaxKey BsonBinary)
           (org.bson.codecs DecoderContext DocumentCodec)
           (org.bson.types Decimal128)
           (java.util Random)))


(defn valid-key? [s]
  (and (string? s)
       (not= s "")
       (not (.startsWith s "$"))))

;; primitives
(s/def :bson/double (s/with-gen #(instance? BsonDouble %)
                      #(gen/fmap (fn [^Double d] (BsonDouble. d))
                                 (gen/double))))
(s/def :bson/string (s/with-gen #(instance? BsonString %)
                      #(gen/fmap (fn [^String s]
                                   (BsonString. s))
                                 (gen/string))))


(s/def ::document (s/map-of string? :bson/value :gen-max 8))


(s/def :bson/object (s/with-gen #(instance? BsonDocument %)
                      #(gen/fmap (fn [m]
                                   (let [document (BsonDocument.)]
                                     (doseq [[k v] m]
                                       (.append document k v))
                                     document))
                                 (s/gen ::document))))
(s/def :bson/array (s/with-gen #(instance? BsonArray %)
                     #(gen/fmap (fn [coll] (BsonArray. coll))
                                (s/gen (s/coll-of :bson/value :gen-max 20)))))

(def ^Random random (Random. 42))
(s/def :bson/bin-data
  (s/with-gen #(instance? BsonBinary %)
    #(gen/fmap (fn [n]
                 (let [ba (byte-array n)
                       _  (.nextBytes random ba)]
                   (BsonBinary. ba)))
               (s/gen (s/int-in 1 1e+6)))))

;; skip undefined
(s/def :bson/object-id (s/with-gen #(instance? BsonObjectId %)
                         #(gen/fmap (fn [_] (BsonObjectId.)) (gen/string))))
(s/def :bson/bool #{BsonBoolean/TRUE BsonBoolean/FALSE})
(s/def :bson/date (s/with-gen #(instance? BsonDateTime %)
                    #(gen/fmap (fn [i] (BsonDateTime. i)) (s/gen pos-int?))))
(s/def :bson/null #{BsonNull/VALUE})
(s/def :bson/regex (s/with-gen #(instance? BsonRegularExpression %)
                     #(gen/fmap (fn [s] (BsonRegularExpression. s)) (gen/string-alphanumeric))))
;; skip DBPointer
;; skip JavaScript
;; skip Symbol
;; skip JavaScript code with scope
(s/def :bson/int (s/with-gen #(instance? BsonInt32 %)
                   #(gen/fmap (fn [i] (BsonInt32. (int i))) (gen/int))))
(s/def :bson/timestamp (s/with-gen #(instance? BsonTimestamp %)
                         #(gen/fmap (fn [^java.util.Date d] (BsonTimestamp. (.getTime d))) (s/gen inst?))))
(s/def :bson/long (s/with-gen #(instance? BsonInt64 %)
                    #(gen/fmap (fn [i] (BsonInt64. i)) (gen/large-integer))))
(s/def :bson/decimal (s/with-gen #(instance? BsonDecimal128 %)
                       #(gen/fmap (fn [i] (BsonDecimal128. (Decimal128/parse (str i)))) (s/gen decimal?))))
(s/def :bson/min-key #{(BsonMinKey.)})
(s/def :bson/max-key #{(BsonMaxKey.)})

;; meta types
(s/def :bson/number (s/or :double  :bson/double
                          :int     :bson/int
                          :long    :bson/long
                          :decimal :bson/decimal))

(s/def :bson/value (s/or :number    :bson/number
                         :string    :bson/string
                         :object    :bson/object
                         :array     :bson/array
                         :binary    :bson/bin-data
                         :object-id :bson/object-id
                         :bool      :bson/bool
                         :date      :bson/date
                         :null      :bson/null
                         :regex     :bson/regex
                         :timestamp :bson/timestamp
                         :min-key   :bson/min-key
                         :max-key   :bson/max-key))


(defn to-document [^BsonDocument document]
  (.decode (DocumentCodec.) (BsonDocumentReader. document) (.build (DecoderContext/builder))))
