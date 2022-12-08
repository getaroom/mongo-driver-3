(ns mongo-driver-3.bench
  (:require [monger.core :as m]
            [monger.collection :as mc]
            [monger.conversion :as mcon]
            [mongo-driver-3.client :as md]
            [mongo-driver-3.collection :as mdc]
            [mongo-driver-3.bson :as bson]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [criterium.core :as crit]
            [mongo-driver-3.codec :as c])
  (:import (org.bson.codecs DecoderContext)
           (org.bson BsonDocumentReader BsonDocument)
           (org.bson.codecs.configuration CodecRegistry)))


(defonce legacy-conn (m/connect-via-uri "mongodb://127.0.0.1/availability_preprod"))
(def legacy-db (:db legacy-conn))
(def legacy-client (:conn legacy-conn))

(set! *warn-on-reflection* true)


(def test-document (gen/generate (s/gen :bson/object)))



(def default-registry (c/clojure-registry))
(def settings (-> (com.mongodb.MongoClientSettings/builder)
                  (.codecRegistry default-registry)
                  ;;(.compressorList [(com.mongodb.MongoCompressor/createSnappyCompressor)])
                  (.build)))
(defonce sync-client (com.mongodb.client.MongoClients/create settings))
(def sync-db (md/get-db sync-client "availability_preprod"))






(defn decode
  "Decodes x according to registry.  Assumes a BSON document with field root."
  ([^BsonDocument x]
   (decode x default-registry))
  ([^BsonDocument x ^CodecRegistry registry]
   (let [ctx    (.build (DecoderContext/builder))
         codec  (.get registry clojure.lang.PersistentArrayMap)
         reader (BsonDocumentReader. x)]
     (.decodeWithChildContext ctx codec reader))))

(def serialized-doc (.toJson (mc/find-one legacy-db "properties" {:_id (org.bson.types.ObjectId. "4c920093e49b9e37e7000001")})))


;; random text, small
(comment
  (let [db-obj (com.mongodb.BasicDBObject/parse serialized-doc)]
    (crit/with-progress-reporting
      (crit/bench
       (mcon/from-db-object db-obj true))))
  ;; Warming up for JIT optimisations 10000000000 ...
  ;; compilation occurred before 2722 iterations
  ;; compilation occurred before 5443 iterations
  ;; Estimating execution count ...
  ;; Sampling ...
  ;; Final GC...
  ;; Checking GC...
  ;; Finding outliers ...
  ;; Bootstrapping ...
  ;; Checking outlier significance
  ;; Evaluation count : 256860 in 60 samples of 4281 calls.
  ;; Execution time mean : 235.492550 µs
  ;; Execution time std-deviation : 4.450019 µs
  ;; Execution time lower quantile : 230.895930 µs ( 2.5%)
  ;; Execution time upper quantile : 248.351333 µs (97.5%)
  ;; Overhead used : 2.269609 ns

  ;; Found 5 outliers in 60 samples (8.3333 %)
  ;; low-severe  3 (5.0000 %)
  ;; low-mild    2 (3.3333 %)
  ;; Variance from outliers : 7.8269 % Variance is slightly inflated by outliers

  (let [bson-doc (org.bson.BsonDocument/parse serialized-doc)]
    (crit/with-progress-reporting
      (crit/bench
       (decode bson-doc))))

  )


(comment
  ;; Query from DB
  (crit/with-progress-reporting
    (crit/quick-bench
     (count (mc/find-maps legacy-db "properties" {:uuid {:$in property-uuid}}))))
  ;; Warming up for JIT optimisations 5000000000 ...
  ;;   compilation occurred before 1 iterations
  ;;   compilation occurred before 2 iterations
  ;;   compilation occurred before 3 iterations
  ;;   compilation occurred before 5 iterations
  ;;   compilation occurred before 6 iterations
  ;;   compilation occurred before 8 iterations
  ;;   compilation occurred before 10 iterations
  ;;   compilation occurred before 12 iterations
  ;;   compilation occurred before 13 iterations
  ;;   compilation occurred before 14 iterations
  ;;   compilation occurred before 16 iterations
  ;;   compilation occurred before 18 iterations
  ;;   compilation occurred before 19 iterations
  ;;   compilation occurred before 21 iterations
  ;;   compilation occurred before 22 iterations
  ;; Estimating execution count ...
  ;; Sampling ...
  ;; Final GC...
  ;; Checking GC...
  ;; Finding outliers ...
  ;; Bootstrapping ...
  ;; Checking outlier significance
  ;; Evaluation count : 6 in 6 samples of 1 calls.
  ;;              Execution time mean : 562.959668 ms
  ;;     Execution time std-deviation : 21.864436 ms
  ;;    Execution time lower quantile : 539.966801 ms ( 2.5%)
  ;;    Execution time upper quantile : 587.193893 ms (97.5%)
  ;;                    Overhead used : 2.269609 ns



  (crit/with-progress-reporting
    (crit/quick-bench
     (count (seq (mdc/find sync-db (.getCollection sync-db "properties" clojure.lang.PersistentArrayMap) {:uuid {:$in property-uuid}} {:raw? true})))))
  ;; Warming up for JIT optimisations 5000000000 ...
  ;;   compilation occurred before 1 iterations
  ;;   compilation occurred before 7 iterations
  ;;   compilation occurred before 9 iterations
  ;;   compilation occurred before 11 iterations
  ;;   compilation occurred before 13 iterations
  ;;   compilation occurred before 15 iterations
  ;;   compilation occurred before 17 iterations
  ;;   compilation occurred before 23 iterations
  ;; Estimating execution count ...
  ;; Sampling ...
  ;; Final GC...
  ;; Checking GC...
  ;; Finding outliers ...
  ;; Bootstrapping ...
  ;; Checking outlier significance
  ;; Evaluation count : 6 in 6 samples of 1 calls.
  ;;              Execution time mean : 431.941196 ms
  ;;     Execution time std-deviation : 9.771901 ms
  ;;    Execution time lower quantile : 419.361476 ms ( 2.5%)
  ;;    Execution time upper quantile : 443.665872 ms (97.5%)
  ;;                    Overhead used : 2.788279 ns

  ;; Found 2 outliers in 6 samples (33.3333 %)
  ;;      low-severe       1 (16.6667 %)
  ;;      low-mild         1 (16.6667 %)
  ;;  Variance from outliers : 13.8889 % Variance is moderately inflated by outliers


  )



(comment
  (mdc/insert-one sync-db "test" (bson/to-document test-document))
  (mdc/find-one sync-db "test" {})

  )



(def property-uuid ["e45d4fd8-2c2f-46e3-98e0-04c5a8963cf0",
                    "fc0fbc15-a894-5638-9d1f-630cbeb483ae",
                    "66ddf411-7fed-5cbc-b9c4-14f46506f1b3",
                    "6f80b8f8-8fc3-5a32-a24c-d0803a0578e5",
                    "33633271-12cc-5b38-ad8c-34d284dbc5e4",
                    "f24d34c5-f713-5b53-96ad-66a2d112c4f7",
                    "2dcc7aa4-007a-5fa5-ab91-5173d6d5d51c",
                    "24a46eda-c4f2-5ce1-b45e-0e37269068ad",
                    "a656a3a0-596b-55e9-b9a1-fb19a307393d",
                    "0065a506-84ae-535f-81fc-40b60b6c493f",
                    "6e297038-d686-554c-8374-9dea8e8e5e89",
                    "25498db8-6c7c-5f05-9198-93f28d8659f0",
                    "65b59e25-a831-5d31-be6f-1aecf8b41e7c",
                    "b4a2bba4-1349-50c8-9cbe-9a299657d2f4",
                    "c9b6c5e4-1b8b-545c-9ee7-cf67114bd965",
                    "ce871aa5-e80e-5cf1-880e-61559370608e",
                    "0a5e137d-49b9-5678-a08f-0128375e67a3",
                    "235a842c-0d15-59fc-aef4-adde7821c2a2",
                    "47696e99-3be5-564c-a300-9b06eb2802c0",
                    "4550e0c0-0398-598d-86b6-5d41415c6db3",
                    "1d4d34e4-0609-560e-85fd-ccd2f1818f12",
                    "082a1e76-c9ff-5304-b3e2-7d1c9c258907",
                    "f3c9b41c-6585-5737-9a64-8aee89e44ffb",
                    "5ac709c9-78ad-41c1-8368-d8f37f3125fa",
                    "7711ea4b-34c7-5fa4-b057-141d242a54d4",
                    "41cddecb-469b-57d4-a512-be6f44fb25c5",
                    "5dadebce-6375-5334-a87d-77f37684fab5",
                    "1d0b47f5-aa44-5d23-bb3d-0777301df446",
                    "0b1b9036-07d6-5bb3-8190-9dba8059b5af",
                    "b144601e-f98d-5911-b2c5-2327389d1225",
                    "11121e6b-6c03-44fb-91ec-9bc7823aca84",
                    "a6d37d08-e165-5dda-b50a-cbdf291f3ccf",
                    "ea3d2cda-5dee-56fc-a327-47bb4776f15d",
                    "77a201cf-a2c4-4e0c-a04b-4e904c224910",
                    "cc94f9e0-b708-5776-b83c-4b86393205c3",
                    "f3151962-8386-5b14-b929-5495bd7d3bab",
                    "f5ac43dc-fdd6-4130-a691-7f305079a9cf",
                    "9aac322a-09ba-580a-9cf1-1752bb2d7b59",
                    "b506ae00-2259-5eb4-acea-9652334a20a3",
                    "dddfa7da-bd1d-4253-af35-98c0189ba106",
                    "d3eebda3-65fb-5a39-99b7-bc5d5603daaa",
                    "901d646d-0cfb-590e-88ca-537199ebdef4",
                    "a198da0a-ae31-452d-9997-af7af9210845",
                    "81cb840d-93ca-5271-9334-87d6daeaef92",
                    "8e1aa07f-d92f-5c00-a450-9285f4187fae",
                    "ebe18dcf-c187-5d7e-8b00-ba3f617094ef",
                    "061eec21-d10c-53d1-b705-63c458f486f3",
                    "1e461930-a34f-50e1-9753-8e9491366d7a",
                    "a8f88fec-cdde-547b-95f2-790446c9d701",
                    "7c79f5b7-e6e1-5288-92de-161ed790485e",
                    "851b9523-27c0-5d5d-a2ae-3f4906c0a7eb",
                    "4cd19518-55b9-57d4-88fc-773cf98ef31a",
                    "7d7c218f-d333-599a-aaeb-a41401061fd9",
                    "03c324c4-b567-5d2b-901b-5644f2fa80cf",
                    "b2f7f847-4dd5-5cc5-b0d2-fb537d09faa7",
                    "d5c07770-adac-5674-9dec-306271354592",
                    "a0050dfe-8540-51b4-ae85-a22301fc644d",
                    "34fba0d5-fc50-5211-aab8-68db2515d7db",
                    "250d6f88-221e-5628-8431-400e4b56550a",
                    "85e3e029-b712-44ab-923f-d3fc7255611b",
                    "2fa8a21f-f383-551e-8bf7-fea94a302926",
                    "ddd2a3d5-e50d-5d0c-b694-bb9f579f8696",
                    "cf3531ee-07d6-567c-bd3c-b7e9c7166abc",
                    "429e02bf-1b1b-51d7-a117-c3d264543c4b",
                    "896824db-0903-5dcc-8f50-a3dba1aa6d5a",
                    "a92ed702-3ec2-5d73-8fa2-6fce6b24485e",
                    "cc1f960c-a5be-5a68-b6e0-17c33f140216",
                    "9923fb65-13d2-544e-b232-6f558cedbb3c",
                    "8d1a7e65-620f-50a0-9517-1b58d0f95edd",
                    "6d36054c-5939-4f95-a88e-ab37bb693b80",
                    "9189eb84-c321-436d-be4c-0f935f0af657",
                    "94a85111-3cab-54e3-ad42-4fab32763557",
                    "b059ace4-c36d-5168-b9c6-3f9139242981",
                    "916132be-8852-586a-95bb-6b1f6790ce52",
                    "d82b4ac9-f75a-531c-974b-b9f8aacc5d43",
                    "cd661204-dd4d-5aa6-ac0a-1296ec39820e",
                    "b90df974-21a9-5fb5-a0f7-7ba5f8f1cbf5",
                    "ea604a0f-0555-55c8-919c-0b5727b297a0",
                    "9d73b31a-c6f1-50ed-b2aa-a2762bc1559e",
                    "220087bf-7caf-5765-83c7-6371aef36fec",
                    "a499893a-3153-516e-ab4b-994a06ef3232",
                    "7b307f97-5ad9-5c47-82cb-ba9807f15de0",
                    "c6c212fd-ed03-57ab-945b-74063d76a408",
                    "8d03b572-8dff-5fde-8409-73d9ba8abba7",
                    "803f27ad-f740-5eda-9254-514cdcbcf9aa",
                    "312e76f7-caaf-56eb-b9dc-ccc6320680e4",
                    "db3e4b8d-2e68-511f-ae03-45d57348397c",
                    "9d62fb83-a021-5d3f-9d77-7517ab74428a",
                    "6e381b28-526b-5438-b55e-f7252e5ddbcf",
                    "0b713b61-368b-461e-a76a-009e87a3693d",
                    "2729e5a8-22be-592f-908f-7990ffc95216",
                    "09c59458-6d7e-593b-90f9-2b41a1e73458",
                    "daa945b6-9edb-5f41-b15a-3cee61deba20",
                    "fa00808d-ffec-569c-94fb-651912784f68",
                    "46cea4cd-6d15-5358-90de-ef18196d1d44",
                    "1d58a02f-eeff-5cbc-a387-fef36208655c",
                    "b8e8e3d3-102f-5a61-8486-12fc2891b371",
                    "63313d79-5d67-573c-ba3f-78e42990498e",
                    "c6133d0f-3012-5d31-a4be-1180fd590d3d",
                    "0af7a3ca-78c7-562a-afc9-7154c44c5b55",
                    "8457cff5-fea1-520b-a9f5-5d4b8fb39866",
                    "cc24bd65-baa7-53fa-a2fe-93381fca3def",
                    "069c8aeb-68b4-56ee-95ea-75a867dce2c9",
                    "101f7d3e-dda5-5b6c-8fc1-890c5e4bed53",
                    "3371022c-98d9-5626-a6e7-790f23a8f8e1",
                    "f7b9fafd-49ab-54de-902f-28b865b2821e",
                    "b4ae310f-7df2-4c69-a6a7-a2a9274d07e5",
                    "fbf18dec-e325-5adf-871b-b7180783850d",
                    "4f6b9f41-632a-514c-bf99-0a8cd26ac211",
                    "530dabe3-001c-51e4-979b-eb45a12f3a36",
                    "a096a0f9-db4a-56ea-89a9-fad363c76684",
                    "f8b38b92-e80b-5b7f-8d1e-1920d9c449ce",
                    "39fb6457-9449-514a-a47e-3ca7fab3dc12",
                    "7b8f9993-cdd6-5874-82a1-6adc194cccca",
                    "bb07e9bd-40b7-52f7-9413-11cb8f3aa7dc",
                    "cdf9dec7-48f0-5881-9bbf-b4f518bff3c4",
                    "5a4907df-aa0d-4327-84a9-f897d1c321c6",
                    "bdd9b0ed-eb1d-572c-b973-91ba3d6b4815",
                    "89cf170b-9526-52de-98a7-01460718fb0c",
                    "ad6082b5-d795-51bb-85fd-2f0da213cbc6",
                    "27335c47-a356-592f-aa92-603f3d5a87eb",
                    "fde7ba29-c680-5a9e-a166-10fd62f454b4",
                    "2fa4e767-8b2c-526a-ac6d-949d4a51288b",
                    "1f1782a2-9b95-5a7e-9f3f-46a315e3549d",
                    "f5369a1b-5eff-56c5-87ae-815141cb3cb7",
                    "f1225aa5-18bf-5ac1-960c-0787d2017cae",
                    "fad5db30-314a-5908-b3c0-85550783adad",
                    "a60108ef-4d4f-5a52-95c1-95c9b8c9a583",
                    "747ae12a-52cb-58e7-bc4a-b128b79ac496",
                    "ae8e76c7-528a-501e-8fac-8f57f82f7acd",
                    "f9f452a8-b90f-56f4-8420-9a93873db71a",
                    "64f12ef0-ae89-552c-89a5-7bf84b88aa53",
                    "a1daa687-7cdb-5c83-9846-b4e4919247c7",
                    "920bcc7e-05fe-5dec-8e3e-51c6daad26e5",
                    "a0fd04ea-5bc7-5d6a-9d6d-f0b2dbcdd8ff",
                    "cf722d29-838a-55d3-b2d5-23710bb1341d",
                    "c0e77ad6-a449-5c7d-a62a-44eec2a2b966",
                    "b3645a41-0528-5968-a7fa-1fe0b8b9889d",
                    "070c1e19-9e57-5aa4-9a7d-9324695ecd52",
                    "2b12f317-2a03-587e-9db6-8998038b859d",
                    "6b995282-e555-5417-b9e9-0ef3034818e2",
                    "a940cfb9-2abe-5835-9d69-41c0d992c38d",
                    "0168a537-b9d8-5616-9daa-b99e1869a4c1",
                    "5cc3f94b-5b41-50d1-a66e-94c6ab5a8b6a",
                    "24cfb3c9-cb21-5288-9ecb-c4414ef69e06",
                    "8ebe5c58-f394-5e38-9c9f-cca6098ae0e2",
                    "bd8c3c31-218f-5570-ac5c-8850d4bcd33f",
                    "59c69d4c-1824-5dfc-95f6-6b8719a56de1",
                    "d1054095-53b1-5ad0-8e67-856e4037ac5a",
                    "25c7d365-4941-552e-8a21-ffbc81097068",
                    "929c1453-a69a-5a28-b306-99f8421f4e3b",
                    "20c9b8bc-ecbf-5c4d-84bb-ebe8dad4975e",
                    "bce24cc1-5eb8-5337-9f69-2001f878c550",
                    "6f934b52-ebbd-51b8-9b6b-df4cec23540d",
                    "2f44aeae-c2cc-5531-975f-1b789063bc72",
                    "737cffc9-fe4a-584d-9b70-3fe8b1a084ac",
                    "35fbf04b-aa19-4d2a-b424-db650152912b",
                    "8c8c39dd-4ed5-5139-94bc-7bcb7b392635",
                    "7f4fc0ca-c379-5a05-950a-129a9c3c241c",
                    "f6185b77-5d69-51cb-9900-9c2b1696cfd4",
                    "d9787d6a-eb37-5a4a-b945-719c8ac943a5",
                    "eb3c0cc2-936e-5c57-87cf-a5aea456d60c",
                    "1e385fa1-2ef4-5a6c-96eb-dc4ff6bbb2e8",
                    "56008947-4882-40f2-ad33-6ca9c745d0a6",
                    "8fd27062-4b6e-56a9-a540-9d749160ad7b",
                    "c93d8ec6-c343-5b43-9777-fd168bdfc630",
                    "633c6c63-183f-5408-b33b-dd725862e696",
                    "e53edbc0-d3d1-5ea3-9965-b5a07a182209",
                    "d7296b95-2ed6-564b-8776-da23da4bb8cc",
                    "fe41de0b-d02f-5548-98c6-849544910ee5",
                    "6b1e51d1-5aef-51e2-bd95-0362c188e02e",
                    "de47c0ab-7a76-5280-bbd6-44ce6f428f70",
                    "76684c6c-18c8-5890-9804-c4a4fefcc8ca",
                    "d75ae94b-965a-5756-b46a-c39e1ab2205a",
                    "80c33c75-6138-5d6e-ae2a-713121e04555",
                    "cfe401e4-5a57-5917-8a14-4f61c61c9a56",
                    "9b47ddab-8b28-5080-afc6-77589e55ce0d",
                    "1de4418f-0731-5a48-bec6-59261a4945f2",
                    "b2dad790-da6b-5214-a22f-7df7dc7c547c",
                    "7b5ebd89-5cfd-590a-ba5c-d4a5047d9b5b",
                    "17c58065-e505-500f-b081-e11dbe4a3185",
                    "81a6356f-8b13-5891-afab-9f67e6a47f20",
                    "e75a4d5a-9e6c-5b9e-ba1a-765a0b27227f",
                    "2334941c-e38e-5696-9bfb-3bbdabe6b5ef",
                    "37681a90-61e0-52d1-a50c-49ae0c2fc651",
                    "f826c7fa-6a98-570c-bda8-8dedc790da8a",
                    "56cafd40-8c9a-52da-9124-2cc049aa8065",
                    "e205ae0e-af4f-5d53-b4d1-36159d3b33bc",
                    "e1c198d5-eb27-5dc6-97c4-b3a791a27ade",
                    "24469e5e-6ccb-57fa-ac13-788cec5e1992",
                    "9111d154-bb96-5ad1-8e84-13a8cbb0ff58",
                    "71d13945-fa98-5cbf-8916-ac9a6e20ceaf",
                    "8e30cb8f-d493-5803-93c2-0f79ad78d836",
                    "40798125-338a-596e-8bd0-6b40febfa82b",
                    "da594d6b-e513-55aa-94cc-d390c461ecef",
                    "725b2b12-f5cc-5de6-a72a-edf600ced505",
                    "cd2c2982-7832-5d54-8c84-70641c62ec23",
                    "555348af-5d49-5b3b-8403-64aede98d630",
                    "b301233f-2abc-56a6-bcfb-22cce313ed7f",
                    "c2523788-daee-55a5-8887-11e236e31f7c",
                    "eef66b5a-10ce-5d69-a877-6580448b5a09",
                    "e1f51adb-8905-5c36-b61a-b730cf8bce40",
                    "bb336fc8-5425-5a79-9178-e6e2c215623b",
                    "98ac3509-bda9-5f3d-844e-2948bf514e58",
                    "48728fbe-34b3-51a5-9023-0aa1c1507e31",
                    "55d37fc1-a9d6-5f73-95c4-de55c5f23348",
                    "bf279a6e-b213-5946-9080-812a18ef8ec5",
                    "1549136d-67c6-56b9-b73f-d6667848939c",
                    "ae6a286b-32c7-558b-aa13-1e18f708a13d",
                    "ebd29d2a-61cf-5089-853a-a9e06096a932",
                    "79f023ca-cd55-50aa-9536-b17657d8f605",
                    "c091d3bd-ce75-5c25-a021-f93b33618197",
                    "db1c7fcf-02b3-5387-b51c-bd3913d81d33",
                    "e8fbb70d-8b81-5075-9845-614f4b015430",
                    "84602e35-9641-5768-8475-a711e0e8b13d",
                    "5a430aa2-c1df-56a5-8385-8011ca0da421",
                    "fa787e35-5b8e-5baf-9b79-bc3571ca2d65",
                    "8edee807-f68a-5eb4-a5b2-37e98d040d93",
                    "9a8a46e5-08c7-5c67-8382-7190ae7a81f3",
                    "57a52358-046e-539c-9326-d25c83b65112",
                    "581c2a23-fa81-5c8e-89b9-1b26ab4f333c",
                    "b1c6ba0a-d0ac-5fff-9831-c2f311df024c",
                    "3e6827ca-3cfe-5df2-a474-d666d7b9896d",
                    "ee6aa7ea-7124-51e4-b262-fa18f9da935a",
                    "30cdf909-1012-59cf-bb92-73e4b7b9a3bc",
                    "f0ae294e-5319-5965-8f08-443c65259b68",
                    "03220916-ca91-40e6-8594-cde19b70d534",
                    "9654075e-0370-5bbc-9a1a-9e9ba6e8fc3f",
                    "bedc2cc4-cba4-5331-9ca4-43b3a49172ee",
                    "b68453d3-7339-5a10-a074-379ec5527e4a",
                    "5b605b91-547a-558d-a8f7-5363806f5537",
                    "67dbf542-2d5e-5f07-aa94-b2e5d807d66f",
                    "7e2d0f14-c166-5528-a0bd-d4c9d5406ed3",
                    "ffb69c6a-28a7-512c-9123-e1d84ba7d144",
                    "aac195b6-62a8-594c-a3fc-f4b185821069",
                    "dd0fc95b-ae39-541f-82da-e81e86b0b450",
                    "9a8dc3cb-8abb-5c98-938b-18c3cef264b3",
                    "34a8275a-ee25-5395-b4ff-3e830303df88",
                    "d266bdf1-d92c-538a-97f5-4b2205f0cc41",
                    "57569fb2-2e80-52b3-bc75-f9cf349af5a0",
                    "7ab7c952-cfc4-55f4-8bf9-79345b7961c7",
                    "ecccae31-a759-5baf-8923-8d2dcb621b56",
                    "e1e6ac20-ca5a-5b16-a42a-cd869e5c8f93",
                    "e46b74d4-07d9-5398-a394-efcb9f8f5105",
                    "15ddac88-7763-5c64-ad20-e0e8c76246f7",
                    "d3d4c056-5ffd-59e4-8220-4ed6e61d708a",
                    "ba314a41-88a6-5111-8932-eb8f2b218741",
                    "f4b4d161-a2ce-4ecb-96e9-11b6e0dc8e33",
                    "07a4cbb5-cdb3-40d7-b584-ace3dec70c59",
                    "707113af-3259-5aa7-854a-e2cccc229048",
                    "8756c4b4-5b60-5740-976b-6da1b3bbd9c7",
                    "c25cc7b6-13a5-5e28-83c5-964a3c03f374",
                    "64cedf71-68b8-5f0e-977b-ff06e1288730",
                    "6e1e7ac5-f776-54e0-a803-9ae5fde9357d",
                    "4412b191-d9d8-5130-a4ae-bbbaf1e06757",
                    "267f6000-fe82-59fe-9633-58e48e80e51e",
                    "91f1027a-93de-55d9-bce8-b71780cae1f3",
                    "898adde0-5e56-5e2a-8307-d52de0df7452",
                    "2ac4dec5-6b86-5a67-9a6f-b1c375594b77",
                    "5a6c1fb0-be39-5056-b01c-862ced10e4d0",
                    "1e378f31-4f4a-5f68-becc-b9f0928d7081",
                    "378b3884-7cc8-5aa3-9ed9-d33c7f3db748",
                    "23604957-6da6-5909-a206-d131cb04acc3",
                    "39110c82-cfba-5cc0-905d-c42123011e75",
                    "2a1b1749-0d23-5069-8046-35f531a299b7",
                    "87c418f9-17e8-54e6-a8fb-4ee195fb597d",
                    "b8520f6c-fa8a-5dfa-b5ba-d46e0e1d519b",
                    "5aa48dc7-b3e4-555f-9cb7-3c91906cae00",
                    "7fa6de39-cd95-54a1-9fc5-26e22fe1e6d9",
                    "3f8f1e91-851f-5b02-9e4a-62762bd614e6",
                    "2dcd1821-ac5f-5cdf-9508-c29ed3f9265e",
                    "f92ea897-a607-5dff-865f-b4d0582b92ad",
                    "0e85395c-d047-5e1a-8f6f-450c129bb32c",
                    "40b86618-f126-576b-b9d3-9b7bbcf44f33",
                    "eb2ef5df-a39d-541d-abe5-094e12999844",
                    "940732e4-6fe1-583a-b6e5-e0e96a6d2d60",
                    "35bcfa3a-a0b5-5fb2-90c0-31adf7b0a592",
                    "291c701b-bb46-5277-8968-f76eda068f61",
                    "654b8f85-532f-5671-a622-e6f3eb89a628",
                    "6442737d-db74-55e7-8c1f-676dc798bf01",
                    "ea87c41c-b6c2-533a-9a0a-e6a83d32426a",
                    "77d3c80b-648a-5cd9-980d-69a9872bbc4e",
                    "2bff9cf1-83f6-5586-81ba-43034b6e103f",
                    "3be83341-7186-5a8c-8516-b7e214d5f8b8",
                    "36779edb-bc4c-5578-b902-d746d92830c2",
                    "2fed7be5-91de-5360-85c2-509a2d22be4e",
                    "c6974ed4-cdb9-5178-9366-bd64a3732d25",
                    "969951d3-23e3-58f6-a062-0a68301d190e",
                    "b9de3493-6d85-5955-8a3d-e89a5953eef5",
                    "1e92ecf3-61b2-5a14-9390-0c48672d21cb",
                    "6d32bb98-64f6-5444-802c-72965056c87e",
                    "9f29716d-fde5-5d88-a567-a8da949d8dab",
                    "37839269-e477-552f-a1d7-9dfbe686d8a5",
                    "cc8f7e0b-60cf-5330-ae49-40843706faba",
                    "e954aa56-8b3b-58c7-beb3-fb1a949dbd5a",
                    "df568a21-720e-510c-9463-3d28a696453b",
                    "fcf8379e-4a18-5f2c-be63-2240c784ad06",
                    "c1b2f9be-042a-5119-a2f7-f2af5b1a242c",
                    "465f34e6-959e-5ec4-8ac5-3d1eca8f6f18",
                    "85fae5d4-b75f-5569-babe-b7e2d634d881",
                    "440cd1ed-9115-5b0b-9faf-a2fd2a2cc8d7",
                    "e9592e42-877c-52a4-ac56-a234bf5e693c",
                    "ba7e11a7-a727-5ee8-813f-f57291935c71",
                    "29719aed-e06a-53ee-ba58-c59d5293be05",
                    "2c7db503-75d5-5256-86db-95812850f389",
                    "5e0697aa-8c0a-5b26-aa8c-9b3cd3f72bfb",
                    "418a50dd-ee76-4733-9df8-360d1f8a5d04",
                    "4e85ae78-b420-5201-afdc-ab8d868b262a",
                    "8dd5b30b-e769-5f17-960c-c6d713c88d18",
                    "c8c1adff-49c9-5612-a683-ee889060c6a5",
                    "1781848b-a63f-5004-8b5c-95c1f96937f7",
                    "4ae3e645-8bfc-5df6-9d8b-90f1a3316281",
                    "8bb7f0e9-104a-58e1-afac-75f3c90d7b9c",
                    "6278d8f1-96bd-5daa-828f-1d7b06e4a6f2",
                    "38f93700-a5d0-59d9-9fcb-8f2d5051a8c2",
                    "81c346f3-1319-54a7-b511-68155d8223b5",
                    "d1c33893-97ea-5f75-9d3f-40ca0b18a684",
                    "bac7ed88-1f32-5386-bacd-3d056472f364",
                    "9d916a1a-e0be-53dc-b9bf-64a76faa98a2",
                    "88bff77e-3ea6-58bd-8486-603c6a2e1bf3",
                    "4b2004e4-4ec5-5704-bb38-92d237738381",
                    "07cb4e9f-fc2e-5ec8-b170-bb5037833da5",
                    "9af18679-12f4-5e70-8fda-b02823377292",
                    "7c0ee5a7-d898-5571-9316-90811fa8a539",
                    "2f65ec9b-e3fe-52af-92a0-8c4b0d8996fa",
                    "3d564de7-eee8-564b-a4f9-bddc1f03d207",
                    "36dce504-42f7-50e8-b0ea-0c49e8df0a09",
                    "aff33b34-845c-517f-bcae-9d365560e5cb",
                    "80a2475d-3df7-5f21-baed-62129a800330",
                    "e75ef0c3-995c-56b3-a917-77c4a662bd54",
                    "1f9ec98c-d5fa-5cce-b7a6-00843fb59ab9",
                    "54f1606d-cad6-56cf-8466-1c93e87811e7",
                    "1033efeb-c989-5c26-9e61-732243477c59",
                    "50915927-c44a-5167-b166-97c137c32e17",
                    "fac1ac69-324e-587c-addf-1e979b20a9dd",
                    "81d95562-abd1-5867-af78-644ecf4a55fc",
                    "655fbed6-6658-5394-8fcd-f3667e2782f6",
                    "aad6e74f-ab5f-59e7-b794-f07fc51f2eb2",
                    "62b713b5-8190-5fa9-b5b9-42f5f9adf3c5",
                    "a4a5c650-952a-519a-9fdd-c85d8f6b7d9c",
                    "878a5e01-c79f-56bb-b156-52775a2d1122",
                    "0ba08ca5-98f8-551b-ad8f-5c56635e2c4e",
                    "94d42864-40bd-5a25-b145-c0d913e4bcd1",
                    "5a5fa97a-2bf9-584e-9110-de03f157bf58",
                    "7ba6b347-1c5a-5f39-a270-453f852a7c3d",
                    "d6a359c0-1068-5ae2-bbc2-39ad92d1b820",
                    "83b764c2-c0f5-5b94-a33e-809de1a83b9e",
                    "dc5a2a17-7fc8-515e-93b6-101e783bc2a0",
                    "8f5eb568-a351-56e6-a5bd-f98701d6f645",
                    "3392768f-5ff4-5899-8f57-0fdcb307314d",
                    "7ece8473-f428-5013-92fc-8373c2400b75",
                    "55b2c298-d8e0-52e0-9903-2c60d63e4635",
                    "cf0bf54d-abb3-56d5-9f21-6936de1cdd23",
                    "8d39d83e-fcbd-57b7-abcc-d657d83d8453",
                    "ec5460b5-718f-5fb3-a35d-a8ba19e8250c",
                    "b37901c4-4cb6-5b06-a652-26d4870a71c8",
                    "0fc12606-3f9c-5b2b-8683-5cba3217044f",
                    "c12425ed-4c45-51f5-abcf-48f5c8bbcf01",
                    "ad3f8247-d30e-599a-9f54-91a2cc760736",
                    "573ff5e3-93d0-5647-924a-7a4adde5e829",
                    "e8932fdc-3bb1-5cdf-9579-8a3da245799b",
                    "97586713-8da6-5da6-b617-f8e49cc45c6e",
                    "2649c686-fe2d-52b4-b123-17dab0e365fb",
                    "0e9bc073-6cc9-5a07-bd09-58ee093648f1",
                    "bb75ff64-3457-54cf-8426-9698da63bf17",
                    "4d2b3141-46af-55e2-bf9f-98f6bf68c383",
                    "6a66126d-ac0f-5469-9f4e-79b2eaa0b17d",
                    "efbc5fc0-43bb-5807-9c90-0026597db9a3",
                    "33f04dd0-c26f-5a34-bcfd-566c8ee10e23",
                    "a0a02e26-69e6-5d84-bdfc-72a070a68df7",
                    "f298595d-8a21-5ece-9955-aa1dbfe17830",
                    "f5520737-0068-570c-884c-a59fb848ca73",
                    "0ad4428c-4caf-5929-bf18-47a0a796fea7",
                    "c0308fe5-b458-5b4b-8a0a-afd1f3f92057",
                    "f70caf88-5fcf-596a-af43-481a5551ad04",
                    "5fb1bcff-5dc0-5768-abbb-594a4493a2a1",
                    "2ab8c779-ba33-596c-b885-0aa95869036a",
                    "56cc6915-1f12-580f-ab1e-0a5c6aa63dc8",
                    "52b7fb7b-4550-537a-af42-72c32cf8d09c",
                    "2666a0ef-6f96-4c03-a44b-f366ef443c6c",
                    "1342df74-43be-5a5b-a788-d4403fac6338",
                    "711b1df0-8899-5426-b607-1d4e753a7d7d",
                    "63a0c809-889f-5aa6-84c6-a2db4b6b1a52",
                    "2c7add44-c3f0-5621-8f04-6d4ef34fbb75",
                    "7af78b86-6bb3-50d5-8c47-2ba13478bc47",
                    "b3fa4c24-bd97-5f8f-833d-aaf0076ca75a",
                    "fe081f7e-1718-512a-89d3-5c87d9d96119",
                    "ae5a81cd-234e-54b7-81ef-eb5e2d52ff46",
                    "d446252d-bb8a-58a1-ad31-494e4fdcd0a8",
                    "20f6c53f-fd16-5cad-94a3-476b2297362a",
                    "6319d376-6b5f-5b24-bf79-287870a2b9ac",
                    "baf14100-201b-5ace-a51c-15696daaa7b6",
                    "e8453768-35aa-54a7-a2ac-22c2017c875d",
                    "47d1baf0-07d4-5b9f-9d25-3efdde7dfa86",
                    "9cf20c0b-4d24-5d8c-9c52-d6dbe2024b0b",
                    "2c097a65-d2e1-5939-b35a-679b8c428702",
                    "802d6c3a-9281-5f7f-9c4c-5e1fbb525f26",
                    "d7999e84-07db-5b02-bb84-25c64702bd4d",
                    "f596a3eb-c7f1-53dd-936a-6993db537194",
                    "8ea30805-8f89-5552-b31f-37bcf3b1c9ae",
                    "a0cbfa19-c34f-5640-b365-a74522964d85",
                    "0e9cd9cf-a976-5049-ae20-25f105bc2bfc",
                    "09429f08-70c1-52b9-bff6-c38c6e2cd9ef",
                    "a56d40fa-2281-5125-9457-5f7fa3a5fec3",
                    "a4eda721-90c1-583e-9a4b-00d166cce4f4",
                    "884e6b4a-5fea-59a4-a24b-135e0b5dbd21",
                    "40d92f9c-f63e-5cbb-961b-72928dbf5851",
                    "c8405f08-42b0-5ae1-81b9-e37183cd64e9",
                    "3d0ec0b3-1b67-5b0e-80c8-e72b9df7d02d",
                    "bed255eb-710d-5c75-96ca-faf9fe555853",
                    "800ec027-ccb0-5920-92e3-1773a03b37e9",
                    "2a5bf30c-51de-5744-822b-a4610a15c1a0",
                    "1cb3b6aa-394e-5451-a5f5-f50704eac021",
                    "67bf636e-99ba-5d2e-b4b3-494de1d0916b",
                    "b3b6e871-67c6-5d03-b8ac-fa8233cc9160",
                    "f19c458d-33e8-5a71-8dcc-f983f0f16fa1",
                    "51485949-c2ec-519f-b807-bbac3b76ba53",
                    "f5ed703e-1c4f-5512-a6d8-3f72fd2a1a6d",
                    "a3c74de3-c122-4f5b-9c9c-e5a42d1c3a68",
                    "9faaaf79-00e1-5212-8075-b9dd2a197ac1",
                    "22ede64d-3509-59c8-b5ff-185ba32bee85",
                    "ee2a62fa-4055-527c-b8d1-3960e0cf9e7a",
                    "9f7279f2-b94c-5d6e-b5bc-3c377e53fbef",
                    "cb288d73-960d-5a27-a69f-99e6006432c1",
                    "1ca00068-18e0-5559-b047-27ad1916de0f",
                    "eb00049a-00b7-56af-80ea-0a7571314416",
                    "85e1bff2-82b2-50a4-9c75-22473cb7c0a1",
                    "c25f2f5f-c63f-52ae-929a-42e9bd88466b",
                    "d0304dd4-7bb8-5448-963e-ac7fedba6f00",
                    "ce0e227a-5a0c-5865-9052-e6ca3a256d8f",
                    "41de88a0-7da8-5ae5-9ba3-2bfe478c7830",
                    "5394e290-4320-510a-8481-bad962407d82",
                    "15331516-46a9-4ac9-8fa9-28e71d9e7387",
                    "9b33f454-baf9-5b17-a179-f74cf7474b26",
                    "06f48b83-4380-5443-be01-62b5d447492f",
                    "6e8f74a4-1f39-579c-8b0b-869eab98651d",
                    "493959dd-d76c-5ed8-9908-fbbd272900ab",
                    "9a4913ce-099e-5b07-a535-1f521d69e400",
                    "56c517e8-110b-57a4-8227-d599ae8c21e5",
                    "16841e19-df11-5414-ad34-53482416f943",
                    "587c8646-cb84-5b9a-bd40-4c92bdfff5cb",
                    "9e481902-b15f-5563-92b5-2ac17832d34e",
                    "b9cbf0e8-4dd1-5070-a1b9-46c7f86221e5",
                    "6d238d58-8d4f-5c97-8419-e3fc9f09f5ca",
                    "52659db1-9f97-50f0-b92e-bdbe93177f47",
                    "6f92857e-f4d9-559a-a747-52a75cf7a5b2",
                    "5e3b7156-6e14-58b7-b336-e7fb182f0ba1",
                    "a3bfbe59-cbea-50b1-8e86-b36fbf9b5e3c",
                    "488eb93d-9824-501f-bd67-07eff918e574",
                    "715f000d-4612-566d-947c-1898bcbfde50",
                    "e20d94a9-c16b-57d6-9c04-d8dedc1c73b1",
                    "6609d425-7b68-595a-a150-04c7ec17d1b4",
                    "9d9004d1-b9de-50b3-a297-c022844b6035",
                    "f15451bd-d2fc-513c-8af2-3a665c6322ae",
                    "ffd711c6-7e52-524a-a9c2-7948dd050ccd",
                    "489adbed-f57d-5686-8c70-f33e478a370b",
                    "c8b6f73d-ef2f-5f74-abdf-d7dd853357f0",
                    "1521bc34-264c-508d-9688-edc8671fa467",
                    "c9b5a405-0e8d-5495-9712-99a725ba1dad",
                    "541f5b49-929c-5263-b40f-7c73d2b63f44",
                    "122ff0f8-a553-5136-baf3-de2a4471772f",
                    "d51e1b87-bd3b-5d23-9207-0f17163f98db",
                    "65f87487-fc96-5924-8058-5080a3ca1123",
                    "d9fea582-ad4a-55e4-8a02-9feadb820ee8",
                    "1f4be9d1-62e7-5530-826f-c6972e79272e",
                    "717395c5-3a9b-56ea-a6b0-8853cc704b18",
                    "3bbbc860-2381-585d-a839-6e193306a811",
                    "b4dd784a-ece0-5d3b-921d-6123c8157fb8",
                    "8f6a9d34-1ed9-5a02-838f-dd8afc1022f1",
                    "7d4cacaa-5d82-5040-b053-ae412c657a6e",
                    "7c4fb8f5-8e80-58b8-935f-f5e1771d3cb6",
                    "6b4106a2-8e06-59c3-b3d5-2da2e65d0074",
                    "ee1af557-b667-5c84-b83c-8a44211c3812",
                    "988ca333-1758-5bdb-accc-9ddb36f15787",
                    "2e8897ee-6a39-5601-9e09-bf5115c4c3b4",
                    "67f81245-dee3-5976-b81d-0f019453e0dd",
                    "32ca332f-b6d3-5736-a50a-62ec7f8353ee",
                    "ccfcaa40-c1eb-52a0-8af3-b7709e00d4b7",
                    "484bb2b9-4d0a-5130-9599-edfd9d582ccb",
                    "50eccabb-bee8-5944-ae53-edafc4bbc0c8",
                    "154b9cee-49e5-55b9-b26f-7fb9b14cb855",
                    "d2f2e1b3-baf4-5ce3-bdf7-352cc5cec224",
                    "797bdced-1ffb-5a01-867e-a4d0931bb3ee",
                    "13418b69-9131-5c6d-98e5-eb2d425bd336",
                    "fbcbe694-4b0c-5bc8-9191-3dcdca36f82d",
                    "a6f9beb1-d3c1-57ba-9b9c-f096b8252aa4",
                    "af862753-2f26-5082-9d79-582d31f10833",
                    "d1b1d8b0-6869-5775-85e3-0c233908610c",
                    "c6455fa2-a7b2-5821-b6ea-f805e420e8b7",
                    "0253a363-1cc7-5b49-9233-4fa23f097348",
                    "a61692c5-6ca1-5c25-9453-6e2f5e2ed879",
                    "34227e50-ba42-5c3c-a815-c1bb993f439c",
                    "052b879e-aeb9-51d8-92c3-71fac39df00a",
                    "7a71dd2b-9e9e-5886-b924-4a78ff414679",
                    "7e818300-906a-53f8-9f28-87e593bac635",
                    "d2d30c0c-6165-53f0-aa35-a6e20e4c29f4",
                    "f20b1cd6-8f0d-5d83-9d68-aa4f46c5659a",
                    "d4031ac0-c178-5eae-81a1-31047e2ae7b2",
                    "4b3e4139-ffc7-524b-869e-f040ac04b110"
                    "15fbcc19-600c-542a-81c6-a5d6091d85d0"
                    "cbd3e3ca-da39-574f-8742-b6401b4842cf"
                    "f65055cb-7610-5223-9477-6e4f38e72b09"
                    "04dbc4a5-92eb-5ed9-ac5f-7fb0b841da13"
                    "57028aad-a300-5c47-9781-d3284966918f"
                    "c39acc94-480d-5d3e-a06c-7fc6cb48dfca"
                    "5e5ccda5-3287-5e3d-9e48-2eb50b4651c7"
                    "967e8928-10a9-56ad-b7e2-1c8c7b5eccce"
                    "d70da315-1246-5712-8ce7-be6d09a9efea"
                    "2c9d7409-3402-59b1-84ef-81b0bdae589f"
                    "52eccb09-2fad-521e-a23a-bcdd2b40860e"
                    "a0f6548c-adc9-5fe0-97b0-a133920fc61b"
                    "a78890d8-39a0-5e32-adfa-07a81f388b86"
                    "c226aae7-42f0-5d27-bd26-3a6c1d7fa65f"
                    "6bae0836-164f-54bc-89c1-3c0e905200fa"
                    "78f8f49b-a5f5-5f66-a81f-ec6fd5b7f9a8"
                    "487349d1-1375-522e-b371-5dafeb0ef3f7"
                    "ce8ad5ac-29bc-5038-8617-9797b543b5e0"
                    "d842cdf8-b5fa-5a75-97d8-e85f73061d6e"
                    "0371e9db-fafd-5937-a130-93d212ce2a1d"
                    "83e39a24-ab1c-53c5-bd91-45255d4f8894"
                    "cf34d9a5-d2e1-5c94-8dd4-b353902c99bc"
                    "d743834d-3699-594b-a5c0-e5db6c12a498"
                    "1b2c44f5-112d-51e6-b898-15d6b30845e0"
                    "317c6917-f250-5199-8df2-caabeb7bfeae"
                    "d0ffaf5c-9994-5e37-9da6-7cbfe0b387b7"
                    "1f464987-570f-54d0-ae8b-e5b04f979027"
                    "2688bc36-1579-552d-8fdc-e204a1282cab"
                    "7e91123c-9925-551a-ac3c-cf0e891feda8"
                    "5bee37d5-dfd4-5a89-a656-bd437dedd241"
                    "743b875a-deb5-5fed-9eda-948bc8ee2e59"
                    "55a3fac2-3522-5208-ba3d-aa578da8e8c7"
                    "fb1078ce-be22-57d6-b97c-f14017783c03"
                    "ba0bccb2-c5ec-53c1-b8fc-5cdb0567f40a"
                    "35c9a0bf-e6ec-59cd-a62a-e682b535276b"
                    "0fac6b9e-7b55-5651-b5af-5296f4da6631"
                    "129d3d70-b0d1-52c8-805c-afcac3d20691"
                    "393be498-4684-57e8-a18b-2fa0195cc0cf"
                    "c0b035b3-22e9-501a-bac1-0382e89e1557"
                    "ee15da16-68ec-5153-aced-4a1de2cff824"
                    "33a30c1d-6862-5b73-b4bc-d3b7017a02a0"
                    "ca50eacf-1eaa-5494-be01-453e63cd1064"
                    "32019a27-2d59-5af6-85ff-6c448550e10a"
                    "bece1a8d-c3f5-5cd3-b482-179f3638ecc3"
                    "1175f0e0-a29f-509f-bccd-a9c7c41aaf97"
                    "03a474c2-9fa9-5278-9561-66d8f018742c"
                    "8e23d003-fb53-5b7e-bbe7-32d71eb1ec98"
                    "7b54ba5c-961b-502f-9abc-9f5a0b5839af"
                    "52a63b3e-b22b-5478-a6e1-426f42e536db"
                    "ec53e70d-75f1-5be7-ba93-c5f05c02018e"
                    "69a6a2f2-9030-5401-a7d9-bef4114a33a4"
                    "9b8b64b0-c078-5afb-b17d-9360440372b1"
                    "63281fdf-01d1-5b26-829d-e0ab1c419ca0"
                    "00bf6511-8a56-597d-b644-1a2552ce350b"
                    "057e7c2e-76bd-5da3-bc61-a9caee9f43a1"
                    "7b8f9993-cdd6-5874-82a1-6adc194cccca"
                    "c0b600c2-8db7-53ee-acb9-44a164bb58e7"
                    "135bd652-1c46-5627-8bc0-b4c590ff7053"
                    "e02f6940-e4ab-5742-bf93-f2b166654a38"
                    "be2509c9-024d-5661-8f0b-280dde788606"
                    "dfeca9c9-0c8a-5bea-8468-5166ff0561a4"
                    "7af75469-d0b5-5c57-a24f-40ed830efd0e"
                    "60460b75-228e-5dba-b023-ef1a93b87c11"
                    "f873d47c-e056-53fa-95d9-3ffad9ca18c6"
                    "23a8d540-56a2-5323-b5f8-d7c9b046927b"
                    "3efc89de-a29d-5b2f-820f-874eb8599ab4"
                    "a2b2d4e0-038c-55f4-9bb6-fdc3903bc9fa"
                    "466fq93f2-d2b6-57ef-a09b-756ecb09ff09"
                    "3c2cf6b3-bb9d-5b15-9995-0bacba6b00c8"
                    "dccbf87f-1cfc-577d-b48c-519950fa15d5"
                    "6d599e8a-005b-5eb7-adf9-f1296d694a3a"
                    "c40ce32f-8468-55fa-b644-adb23017f7ea"
                    "8407c4c8-8c7d-5d22-9ce3-87a43916f29d"
                    "21c166e9-af48-5117-8db5-4451802841b4"
                    "f0a3355f-c859-5e24-967c-b49b7e76b6a2"
                    "bd73c59c-a9ce-5d4c-a089-6418302ba925"
                    "023c91ae-1e1e-5fbe-8e4f-feeafe0be639"
                    "b371e588-d230-5a0b-af8f-1dc01c3c522b"
                    "615a0d43-8f8b-5010-a0dc-e538f427c391"
                    "78d69708-db46-53fd-b27e-88912a76129f"])
