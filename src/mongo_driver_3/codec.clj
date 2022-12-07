(ns mongo-driver-3.codec
  (:import (org.bson BsonType Document BsonWriter BsonReader)
           (org.bson.codecs Codec EncoderContext DecoderContext BsonTypeClassMap BsonTypeCodecMap)
           (org.bson.codecs.configuration CodecRegistry CodecRegistries CodecProvider)
           (com.mongodb MongoClientSettings)))



(set! *warn-on-reflection* true)


(def bson-types
  {BsonType/ARRAY      clojure.lang.PersistentVector
   BsonType/INT32      java.lang.Long
   BsonType/DECIMAL128 java.math.BigDecimal
   BsonType/DOCUMENT   clojure.lang.PersistentArrayMap})


(def driver-default-registry (MongoClientSettings/getDefaultCodecRegistry))


(defmacro typed-array
  [klass coll]
  (let [^Class resolved (resolve klass)]
    (with-meta
      (list 'into-array resolved coll)
      {:tag (str "[L" (.getName resolved) ";")})))


(defn read-value
  [^BsonReader reader ^DecoderContext ctx ^BsonTypeCodecMap codec-map]
  (let [t (.getCurrentBsonType reader)]
    (condp = t
      BsonType/NULL nil
      )))


(defn ^Codec vector-codec
  "Codec for clojure.lang.PersistentVector"
  [^CodecRegistry registry ^BsonTypeClassMap class-map {:as opts}]
  (let [codec-map (BsonTypeCodecMap. class-map registry)]
    (reify Codec
      (getEncoderClass [_] clojure.lang.PersistentVector)

      (encode [_ writer x ctx]
        (.writeStartArray writer)
        (doseq [value x]
          (if (nil? value)
            (.writeNull writer)
            (.encodeWithChildContext ctx (.get registry (type value)) writer value)))
        (.writeEndArray writer))

      (decode [_ reader ctx]
        (.readStartArray reader)
        (loop [coll (transient [])
               t    (.readBsonType reader)]
          (if (= t BsonType/END_OF_DOCUMENT)
            (do (.readEndArray reader)
                (persistent! coll))
            (recur (conj! coll (.decode (.get codec-map t) reader ctx)) (.readBsonType reader))))))))


(defn ^Codec map-codec
  "Codec for clojure.lang.PersistentArrayMap"
  [^CodecRegistry registry ^BsonTypeClassMap class-map {:keys [keyword?] :or {keyword? true}}]
  (let [codec-map (BsonTypeCodecMap. class-map registry)]
    (reify Codec
      (getEncoderClass [_] clojure.lang.PersistentArrayMap)

      (encode [_ writer x ctx]
        (.writeStartDocument writer)
        (doseq [[k v] x]
          (.writeName writer (name k))
          (if (nil? v)
            (.writeNull writer)
            (.encodeWithChildContext ctx (.get registry (type v)) writer v)))
        (.writeEndDocument writer))

      (decode [_ reader ctx]
        (println "decoding map")
        (.readStartDocument reader)
        (loop [doc (transient {})
               t   (.readBsonType reader)]
          (if (= t BsonType/END_OF_DOCUMENT)
            (do (.readEndDocument reader)
                (persistent! doc))
            (let [k (cond-> (.readName reader)
                      keyword? keyword)
                  v (.decode (.get codec-map t) reader ctx)]
              (recur (assoc! doc k v) (.readBsonType reader)))))))))


(defn codec-provider
  "Contructs a CodecProvider using f that matches t using class-map"
  [f t ^BsonTypeClassMap class-map opts]
  (reify CodecProvider
    (get [_ clazz registry]
      (when (= t clazz)
        (f registry class-map opts)))))


(defn ^CodecRegistry clojure-registry
  "Returns a CodecRegistry for encoding/decoding Clojure data types"
  [bson-type-map]
  (let [opts                {}
        class-map           (BsonTypeClassMap. bson-type-map)
        map-provider        (codec-provider map-codec clojure.lang.PersistentArrayMap class-map opts)
        vector-provider     (codec-provider vector-codec clojure.lang.PersistentVector class-map opts)
        provider-registries (CodecRegistries/fromProviders (typed-array CodecProvider [vector-provider map-provider]))]
    (CodecRegistries/fromRegistries (typed-array CodecRegistry [provider-registries driver-default-registry]))))


(comment
  (require '[criterium.core :as crit])
  (require '[mongo-driver-3.model :as m])

  (require '[clojure.spec.gen.alpha :as gen])

  (def (m/document [1 2 3]))
  )


(require '[clojure.spec.alpha :as s])
(require '[clojure.spec.gen.alpha :as gen])



;; primitives
(s/def :bson/double (s/with-gen #(instance? org.bson.BsonDouble %) #(gen/fmap (fn [^Double d] (org.bson.BsonDouble. d)) (gen/double))))
(s/def :bson/string (s/with-gen #(instance? org.bson.BsonString %) #(gen/fmap (fn [^String s] (org.bson.BsonString. s)) (gen/string))))
(s/def :bson/object (s/map-of :bson/value :bson/value))
(s/def :bson/array nil)
(s/def :bson/binary-data nil)
(s/def :bson/undefined nil)
(s/def :bson/object-id nil)
(s/def :bson/boolean nil)
(s/def :bson/date nil)
(s/def :bson/null nil)
(s/def :bson/regex nil)
(s/def :bson/db-pointer nil)
(s/def :bson/javascript nil)
(s/def :bson/symbol nil)
(s/def :bson/javascript-with-code nil)
(s/def :bson/int32 nil)
(s/def :bson/timestamp nil)
(s/def :bson/int64 nil)
(s/def :bson/decimal128 nil)
(s/def :bson/min-key nil)
(s/def :bson/max-key nil)

;; meta types
(s/def :bson/number (s/or :double :bson/double
                          :int32 :bson/int32
                          :int64 :bson/int64
                          :decimal128 :bson/decimal128))

(s/def :bson/value (s/or :number :bson/number
                         :string :bson/string
                         :object :bson/object))

;; (s/def :bson/string string?)
;; (s/def :bson/object )
