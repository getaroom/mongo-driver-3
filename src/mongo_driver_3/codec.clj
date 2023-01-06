(ns mongo-driver-3.codec
  (:require [clj-time.coerce :as coerce])
  (:import (org.bson BsonType Document BsonWriter BsonReader)
           (org.bson.codecs BsonNullCodec Codec EncoderContext DecoderContext BsonTypeClassMap BsonTypeCodecMap)
           (org.bson.codecs.configuration CodecRegistry CodecRegistries CodecProvider)
           (com.mongodb MongoClientSettings)))



(set! *warn-on-reflection* true)


(defmulti mongo-codec
  "Provides an instance of Codec. The multimethod will be cached for each unique value of t via org.bson.internal.CodecCache."
  (fn [^Class t ^CodecRegistry registry ^BsonTypeClassMap class-map opts]
    t))


(defmethod mongo-codec :default
  [^Class t ^CodecRegistry registry ^BsonTypeClassMap class-map opts])


(defmethod mongo-codec clojure.lang.Keyword
  [t _ _ opts]
  (reify Codec
    (getEncoderClass [_] t)

    (encode [_ writer x _]
      (.writeString writer (name x)))

    (decode [_ reader ctx]
      (assert false "decoding BSON values as clojure.lang.Keyword is not implemented"))))

(defmethod mongo-codec org.joda.time.DateTime
  [t _ _ opts]
  (reify Codec
    (getEncoderClass [_] t)

    (encode [_ writer x _]
      (.writeDateTime writer (coerce/to-long x)))

    (decode [_ reader ctx]
      (coerce/from-long (.readDateTime reader)))))

(defmethod mongo-codec clojure.lang.APersistentVector
  [^Class t ^CodecRegistry registry ^BsonTypeClassMap class-map _]
  (let [codec-map (BsonTypeCodecMap. class-map registry)]
    (reify Codec
      (getEncoderClass [_] t)

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
            (let [value (condp = t
                          BsonType/NULL (.readNull reader)
                          (.decode (.get codec-map t) reader ctx))]
              (recur (conj! coll value) (.readBsonType reader)))))))))


(defmethod mongo-codec clojure.lang.APersistentMap
  [^Class t ^CodecRegistry registry ^BsonTypeClassMap class-map {:keys [keyword?] :or {keyword? true}}]
  (let [codec-map (BsonTypeCodecMap. class-map registry)]
    (reify Codec
      (getEncoderClass [_] t)

      (encode [_ writer x ctx]
        (.writeStartDocument writer)
        (doseq [[k v] x]
          (.writeName writer (name k))
          (if (nil? v)
            (.writeNull writer)
            (.encodeWithChildContext ctx (.get registry (type v)) writer v)))
        (.writeEndDocument writer))

      (decode [_ reader ctx]
        (.readStartDocument reader)
        (loop [doc (transient {})
               t   (.readBsonType reader)]
          (if (= t BsonType/END_OF_DOCUMENT)
            (do (.readEndDocument reader)
                (persistent! doc))
            (let [k (cond-> (.readName reader)
                      keyword? keyword)
                  v (condp = t
                      BsonType/NULL (.readNull reader)
                      (.decode (.get codec-map t) reader ctx))]
              (recur (assoc! doc k v) (.readBsonType reader)))))))))

(def default-bson-types
  {BsonType/ARRAY      clojure.lang.PersistentVector
   BsonType/DATE_TIME  org.joda.time.DateTime
   BsonType/INT32      java.lang.Long
   BsonType/DECIMAL128 java.math.BigDecimal
   BsonType/DOCUMENT   clojure.lang.PersistentArrayMap})


(defmacro typed-array
  [klass coll]
  (let [^Class resolved (resolve klass)]
    (with-meta
      (list 'into-array resolved coll)
      {:tag (str "[L" (.getName resolved) ";")})))


(defn ^CodecRegistry clojure-registry
  "Returns a CodecRegistry for encoding/decoding Clojure data types.  bson-type-map, a map overrides decoding behavior of BSON types."
  ([]
   (clojure-registry default-bson-types {}))
  ([bson-type-map]
   (clojure-registry bson-type-map {}))
  ([bson-type-map {:as opts}]
   (let [class-map       (BsonTypeClassMap. bson-type-map)
         provider        (reify CodecProvider
                           (get [_ clazz registry]
                             (mongo-codec clazz registry class-map opts)))]
     (CodecRegistries/fromRegistries
      (typed-array CodecRegistry [(CodecRegistries/fromCodecs (typed-array Codec []))
                                  (CodecRegistries/fromProviders (typed-array CodecProvider [provider]))
                                  (MongoClientSettings/getDefaultCodecRegistry)])))))
