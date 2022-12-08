(ns mongo-driver-3.codec
  (:import (org.bson BsonType Document BsonWriter BsonReader)
           (org.bson.codecs Codec EncoderContext DecoderContext BsonTypeClassMap BsonTypeCodecMap)
           (org.bson.codecs.configuration CodecRegistry CodecRegistries CodecProvider)
           (com.mongodb MongoClientSettings)))



(set! *warn-on-reflection* true)


(def driver-default-registry (MongoClientSettings/getDefaultCodecRegistry))


(defn ^Codec sequential-codec
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
  "Codec for clojure.lang.APersistent"
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

(def default-bson-types
  {BsonType/ARRAY      clojure.lang.PersistentVector
   BsonType/INT32      java.lang.Long
   BsonType/DECIMAL128 java.math.BigDecimal
   BsonType/DOCUMENT   clojure.lang.PersistentArrayMap})


(def default-sequential-types
  [clojure.lang.PersistentVector])


(def default-map-types
  [clojure.lang.PersistentArrayMap
   clojure.lang.PersistentHashMap])


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
  ([bson-type-map {:keys [map-classes sequential-classes] :as opts
                   :or {map-classes      default-map-types
                        sequential-types default-sequential-types}}]
   (let [class-map            (BsonTypeClassMap. bson-type-map)
         map-providers        (map #(codec-provider map-codec % class-map opts) map-classes)
         sequential-providers (map #(codec-provider sequential-codec % class-map opts) sequential-classes)
         provider-registries  (CodecRegistries/fromProviders (typed-array CodecProvider (concat sequential-providers map-providers)))]
     (CodecRegistries/fromRegistries (typed-array CodecRegistry [provider-registries driver-default-registry])))))
