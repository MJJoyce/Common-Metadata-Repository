(ns cmr.metadata-db.int-test.concepts.service-association-save-test
  "Contains integration tests for saving service associations. Tests saves with various
   configurations including checking for proper error handling."
  (:require
   [clojure.test :refer :all]
   [cmr.metadata-db.int-test.concepts.concept-save-spec :as c-spec]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

(defmethod c-spec/gen-concept :service-association
  [_ _ uniq-num attributes]
  (let [concept-attributes (or (:concept-attributes attributes) {})
        concept (util/create-and-save-collection "REG_PROV" uniq-num 1 concept-attributes)
        service-attributes (or (:service-attributes attributes) {})
        service (util/create-and-save-service "REG_PROV" uniq-num 1 service-attributes)
        attributes (dissoc attributes :concept-attributes :service-attributes)]
    (util/service-association-concept concept service uniq-num attributes)))

;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest save-service-association-test
  (c-spec/general-save-concept-test :service-association ["CMR"]))

(deftest save-service-association-failure-test
  (testing "saving new service associations on non system-level provider"
    (let [coll (util/create-and-save-collection "REG_PROV" 1)
          service (util/create-and-save-service "REG_PROV" 1)
          service-association (-> (util/service-association-concept coll service 2)
                                   (assoc :provider-id "REG_PROV"))
          {:keys [status errors]} (util/save-concept service-association)]

      (is (= 422 status))
      (is (= [(str "Service association could not be associated with provider [REG_PROV]. "
                   "Service associations are system level entities.")]
             errors)))))
